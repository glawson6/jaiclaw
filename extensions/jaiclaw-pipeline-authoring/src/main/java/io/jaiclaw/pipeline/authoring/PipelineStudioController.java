package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.validation.PipelineValidator;
import io.jaiclaw.pipeline.validation.ValidationError;
import io.jaiclaw.pipeline.validation.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST surface for the Pipeline Studio authoring plane. Every endpoint
 * lives under {@code /api/pipeline-studio/*}.
 *
 * <p>Endpoints (mapping to plan B2 / B3 / B7 / B9 / B12):
 *
 * <table>
 * <tr><td>{@code GET /drafts}</td><td>list drafts for the current tenant</td></tr>
 * <tr><td>{@code GET /drafts/{id}}</td><td>single draft</td></tr>
 * <tr><td>{@code POST /drafts}</td><td>create draft; revision starts at 1</td></tr>
 * <tr><td>{@code PUT /drafts/{id}}</td><td>update; requires {@code If-Match} revision header; 409 on mismatch</td></tr>
 * <tr><td>{@code DELETE /drafts/{id}}</td><td>delete</td></tr>
 * <tr><td>{@code POST /drafts/{id}/validate}</td><td>validate stored draft; returns {@link ValidationReport}</td></tr>
 * <tr><td>{@code POST /validate}</td><td>validate anonymous JSON body (live edits)</td></tr>
 * <tr><td>{@code GET /catalog}</td><td>palette + inspector metadata (B6)</td></tr>
 * <tr><td>{@code GET /schema}</td><td>JSON Schema for {@link PipelineDefinition} (B7)</td></tr>
 * <tr><td>{@code GET /drafts/{id}/yaml}</td><td>YAML export (B9)</td></tr>
 * <tr><td>{@code POST /import}</td><td>YAML → new draft (B9)</td></tr>
 * <tr><td>{@code GET /drafts/{id}/variables?stage=X}</td><td>template variables at stage X (B12)</td></tr>
 * </table>
 *
 * <p>Authorization: none in Phase 1. Every endpoint delegates to the
 * app's Spring Security chain. Phase 3 adds role-gated method
 * annotations + a UI-origin URI-scheme allowlist enforced by the
 * validator.
 */
@RestController
@RequestMapping("/api/pipeline-studio")
public class PipelineStudioController {

    private static final Logger log = LoggerFactory.getLogger(PipelineStudioController.class);

    private final PipelineDraftStore draftStore;
    private final PipelineCatalogService catalogService;
    private final PipelineValidator validator;
    private final ObjectMapper json = new ObjectMapper();
    private final YAMLMapper yaml = YAMLMapper.builder().build();
    private final Resource schemaResource = new ClassPathResource(
            "META-INF/jaiclaw-pipeline-schema.json");

    public PipelineStudioController(PipelineDraftStore draftStore,
                                     PipelineCatalogService catalogService,
                                     PipelineValidator validator) {
        this.draftStore = draftStore;
        this.catalogService = catalogService;
        this.validator = validator;
    }

    // ── drafts CRUD ─────────────────────────────────

    @GetMapping("/drafts")
    public List<PipelineDraft> listDrafts() {
        return draftStore.findAll();
    }

    @GetMapping("/drafts/{id}")
    public ResponseEntity<PipelineDraft> getDraft(@PathVariable String id) {
        return draftStore.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/drafts")
    public ResponseEntity<?> createDraft(@RequestBody PipelineDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "PipelineDefinition with non-blank id required"));
        }
        if (draftStore.find(definition.id()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Draft already exists for id: " + definition.id()));
        }
        PipelineDraft fresh = new PipelineDraft(
                definition.id(),
                1L,
                definition,
                currentTenantId(),
                PipelineDraft.Status.DRAFT,
                PipelineDraft.Origin.STUDIO,
                Instant.now());
        PipelineDraft stored = draftStore.save(fresh);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.ETAG, "\"" + stored.revision() + "\"")
                .body(stored);
    }

    @PutMapping("/drafts/{id}")
    public ResponseEntity<?> updateDraft(@PathVariable String id,
                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                          @RequestBody PipelineDefinition definition) {
        Optional<PipelineDraft> existing = draftStore.find(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        long expectedRevision = parseRevisionHeader(ifMatch, existing.get().revision());
        PipelineDraft merged = new PipelineDraft(
                id,
                expectedRevision,
                definition,
                currentTenantId(),
                existing.get().status(),
                existing.get().origin(),
                Instant.now());
        try {
            PipelineDraft stored = draftStore.save(merged);
            return ResponseEntity.ok()
                    .header(HttpHeaders.ETAG, "\"" + stored.revision() + "\"")
                    .body(stored);
        } catch (PipelineDraftStore.OptimisticLockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Revision conflict — reload before saving",
                            "expected", e.expectedRevision(),
                            "actual", e.actualRevision()));
        }
    }

    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<Void> deleteDraft(@PathVariable String id) {
        draftStore.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── validate ────────────────────────────────────

    @PostMapping("/drafts/{id}/validate")
    public ResponseEntity<?> validateDraft(@PathVariable String id) {
        Optional<PipelineDraft> draft = draftStore.find(id);
        if (draft.isEmpty()) return ResponseEntity.notFound().build();
        ValidationReport report = validator.validate(draft.get().definition());
        return ResponseEntity.ok(toReportJson(report));
    }

    @PostMapping("/validate")
    public Map<String, Object> validateAnonymous(@RequestBody PipelineDefinition definition) {
        return toReportJson(validator.validate(definition));
    }

    /**
     * Defense-in-depth for the /validate endpoint (and any other endpoint on
     * this controller that binds a request body). If Jackson can't construct
     * the target type — malformed JSON, wrong field types, a value-layer
     * invariant that throws — return a {@link ValidationReport}-shaped body
     * with a single {@code BODY_PARSE_ERROR} entry so the SPA's
     * ValidationPanel can render it, instead of the default framework 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null && root.getMessage() != null
                ? root.getMessage()
                : ex.getMessage();
        ValidationReport report = new ValidationReport.Builder()
                .addPipelineError("*", new ValidationError(
                        "*",
                        "body",
                        "BODY_PARSE_ERROR",
                        "Could not parse request body: " + message,
                        null))
                .build();
        return ResponseEntity.ok(toReportJson(report));
    }

    // ── catalog + schema ────────────────────────────

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return catalogService.catalog();
    }

    @GetMapping(value = "/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> schema() {
        if (!schemaResource.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(schemaResource);
    }

    // ── YAML round-trip ─────────────────────────────

    @GetMapping(value = "/drafts/{id}/yaml", produces = "application/x-yaml")
    public ResponseEntity<String> exportYaml(@PathVariable String id) {
        Optional<PipelineDraft> draft = draftStore.find(id);
        if (draft.isEmpty()) return ResponseEntity.notFound().build();
        try {
            String body = yaml.writeValueAsString(draft.get().definition());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-yaml")
                    .body(body);
        } catch (Exception e) {
            log.warn("YAML export failed for draft {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/import", consumes = {"application/x-yaml", "text/yaml", "text/plain"})
    public ResponseEntity<?> importYaml(@RequestBody String yamlBody,
                                         @RequestParam(value = "id", required = false) String requestedId) {
        try {
            PipelineDefinition definition = yaml.readValue(yamlBody, PipelineDefinition.class);
            String draftId = definition.id();
            if (draftId == null || draftId.isBlank()) {
                draftId = requestedId != null && !requestedId.isBlank()
                        ? requestedId
                        : "imported-" + UUID.randomUUID().toString().substring(0, 8);
            }
            PipelineDraft fresh = new PipelineDraft(
                    draftId,
                    1L,
                    definition,
                    currentTenantId(),
                    PipelineDraft.Status.DRAFT,
                    PipelineDraft.Origin.YAML_IMPORT,
                    Instant.now());
            PipelineDraft stored = draftStore.save(fresh);
            return ResponseEntity.status(HttpStatus.CREATED).body(stored);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "YAML parse failed: " + e.getMessage()));
        }
    }

    // ── template variables ──────────────────────────

    @GetMapping("/drafts/{id}/variables")
    public ResponseEntity<?> variables(@PathVariable String id,
                                        @RequestParam(value = "stage", required = false) String stage) {
        Optional<PipelineDraft> draft = draftStore.find(id);
        if (draft.isEmpty()) return ResponseEntity.notFound().build();
        PipelineDefinition definition = draft.get().definition();
        PipelineContext ctx = simulateContextUpTo(definition, stage);
        Map<String, String> vars = ctx.availableVariables();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stage", stage);
        body.put("variables", vars);
        return ResponseEntity.ok(body);
    }

    /**
     * Build a synthetic {@link PipelineContext} carrying stage outputs
     * for every stage BEFORE {@code targetStage} in declaration order.
     * Outputs are empty strings — Studio just needs the set of
     * resolvable placeholder names, not their values.
     */
    private static PipelineContext simulateContextUpTo(PipelineDefinition def, String targetStage) {
        List<StageDefinition> stages = def.stages() != null ? def.stages() : List.of();
        Map<String, PipelineContext.StageOutput> outputs = new LinkedHashMap<>();
        for (StageDefinition s : stages) {
            if (s.name().equals(targetStage)) break;
            outputs.put(s.name(),
                    new PipelineContext.StageOutput("", Map.of(), Instant.now()));
        }
        return new PipelineContext(
                def.id(),
                UUID.randomUUID().toString(),
                null,
                null,
                outputs.size(),
                stages.size(),
                null,
                null,
                outputs,
                Map.of(PipelineContext.INPUT_METADATA_KEY, ""));
    }

    // ── helpers ─────────────────────────────────────

    private static String currentTenantId() {
        return Optional.ofNullable(TenantContextHolder.get())
                .map(c -> c.getTenantId())
                .orElse(null);
    }

    private static long parseRevisionHeader(String header, long fallback) {
        if (header == null || header.isBlank()) return fallback;
        String stripped = header.trim();
        if (stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        try {
            return Long.parseLong(stripped);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Map<String, Object> toReportJson(ValidationReport report) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hasErrors", report.hasErrors());
        body.put("formatted", report.formatted());
        List<Map<String, Object>> errors = new ArrayList<>();
        report.byPipeline().values().forEach(list -> list.forEach(err -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("pipelineId", err.pipelineId());
            e.put("location", err.location());
            e.put("code", err.code());
            e.put("message", err.message());
            e.put("suggestion", err.suggestion());
            errors.add(e);
        }));
        body.put("errors", errors);
        return body;
    }
}
