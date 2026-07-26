package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.validation.ValidationError;
import io.jaiclaw.pipeline.validation.ValidationReport;
import io.jaiclaw.pipeline.validation.PipelineValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST surface for Phase 3 of the Pipeline Studio buildout:
 * hot deploy + undeploy + redeploy + test-run.
 *
 * <p>Every endpoint carries a {@code @PreAuthorize} annotation with a
 * SpEL expression that consults the configured role names. When the
 * consuming app has Spring Security wired, the expressions enforce
 * role-based access. When no security is on the classpath the
 * annotations are inert — the app's chain (or lack of one) governs.
 *
 * <p>Endpoints under {@code /api/pipeline-studio/*}:
 * <table>
 *   <tr><td>{@code POST /drafts/{id}/deploy}</td><td>deployer</td></tr>
 *   <tr><td>{@code POST /drafts/{id}/undeploy}</td><td>deployer</td></tr>
 *   <tr><td>{@code POST /drafts/{id}/redeploy}</td><td>deployer</td></tr>
 *   <tr><td>{@code POST /drafts/{id}/test-run}</td><td>author</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/pipeline-studio")
public class PipelineDeploymentController {

    private static final Logger log = LoggerFactory.getLogger(PipelineDeploymentController.class);

    private final PipelineDraftStore draftStore;
    private final PipelineLifecycleManager lifecycle;
    private final PipelineTestRunService testRunService;
    private final PipelineValidator validator;
    private final UriSchemeAllowlist uriAllowlist;

    public PipelineDeploymentController(PipelineDraftStore draftStore,
                                         PipelineLifecycleManager lifecycle,
                                         PipelineTestRunService testRunService,
                                         PipelineValidator validator,
                                         UriSchemeAllowlist uriAllowlist) {
        this.draftStore = draftStore;
        this.lifecycle = lifecycle;
        this.testRunService = testRunService;
        this.validator = validator;
        this.uriAllowlist = uriAllowlist;
    }

    // ── deploy / undeploy / redeploy ────────────────

    @PostMapping("/drafts/{id}/deploy")
    @PreAuthorize("@pipelineAuthzExpressions.deployer()")
    public ResponseEntity<?> deploy(@PathVariable String id) {
        Optional<PipelineDraft> draft = draftStore.find(id);
        if (draft.isEmpty()) return ResponseEntity.notFound().build();
        PipelineDraft d = draft.get();

        ValidationReport core = validator.validate(d.definition());
        ValidationReport uri = uriAllowlist.check(d.definition(), d.origin());
        ValidationReport combined = combine(core, uri);
        if (combined.hasErrors()) {
            return ResponseEntity.badRequest().body(toReportJson(combined));
        }

        try {
            PipelineDefinition deployed = lifecycle.deploy(d.definition(),
                    d.origin() == null ? "STUDIO" : d.origin().name());
            draftStore.save(d.withStatus(PipelineDraft.Status.DEPLOYED));
            return ResponseEntity.ok(Map.of(
                    "pipelineId", deployed.id(),
                    "status", "DEPLOYED",
                    "stageCount", deployed.stages() == null ? 0 : deployed.stages().size()));
        } catch (PipelineLifecycleManager.AlreadyDeployedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage(), "pipelineId", e.pipelineId()));
        } catch (Exception e) {
            log.warn("Deploy failed for '{}': {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/drafts/{id}/undeploy")
    @PreAuthorize("@pipelineAuthzExpressions.deployer()")
    public ResponseEntity<?> undeploy(@PathVariable String id) {
        Optional<PipelineDefinition> removed = lifecycle.undeploy(id, "undeploy");
        if (removed.isEmpty()) return ResponseEntity.notFound().build();
        draftStore.find(id).ifPresent(d ->
                draftStore.save(d.withStatus(PipelineDraft.Status.VALIDATED)));
        return ResponseEntity.ok(Map.of(
                "pipelineId", removed.get().id(),
                "status", "UNDEPLOYED"));
    }

    @PostMapping("/drafts/{id}/redeploy")
    @PreAuthorize("@pipelineAuthzExpressions.deployer()")
    public ResponseEntity<?> redeploy(@PathVariable String id,
                                       @RequestBody(required = false) PipelineDefinition body) {
        Optional<PipelineDraft> draft = draftStore.find(id);
        if (draft.isEmpty()) return ResponseEntity.notFound().build();
        PipelineDefinition next = body != null ? body : draft.get().definition();
        if (next == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Redeploy requires a body or a stored draft"));
        }
        ValidationReport core = validator.validate(next);
        ValidationReport uri = uriAllowlist.check(next, draft.get().origin());
        ValidationReport combined = combine(core, uri);
        if (combined.hasErrors()) {
            return ResponseEntity.badRequest().body(toReportJson(combined));
        }
        try {
            PipelineDefinition deployed = lifecycle.redeploy(id, next,
                    draft.get().origin() == null ? "STUDIO" : draft.get().origin().name());
            return ResponseEntity.ok(Map.of(
                    "pipelineId", deployed.id(),
                    "status", "REDEPLOYED"));
        } catch (Exception e) {
            log.warn("Redeploy failed for '{}': {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── test-run ────────────────────────────────────

    @PostMapping("/drafts/{id}/test-run")
    @PreAuthorize("@pipelineAuthzExpressions.author()")
    public ResponseEntity<?> testRun(@PathVariable String id,
                                      @RequestBody(required = false) TestRunRequest request) {
        Optional<PipelineDraft> draft = draftStore.find(id);
        if (draft.isEmpty()) return ResponseEntity.notFound().build();
        PipelineDraft d = draft.get();

        ValidationReport core = validator.validate(d.definition());
        ValidationReport uri = uriAllowlist.check(d.definition(), d.origin());
        ValidationReport combined = combine(core, uri);
        if (combined.hasErrors()) {
            return ResponseEntity.badRequest().body(toReportJson(combined));
        }

        String input = request == null || request.input() == null ? "" : request.input();
        Duration timeout = request == null || request.timeoutSeconds() == null
                ? null : Duration.ofSeconds(request.timeoutSeconds());
        String tenant = Optional.ofNullable(TenantContextHolder.get())
                .map(c -> c.getTenantId())
                .orElse(null);

        Instant started = Instant.now();
        PipelineTestRunService.TestRunResult result = testRunService.run(d, input, tenant, timeout);
        return ResponseEntity.ok(Map.of(
                "executionId", result.executionId(),
                "status", result.status(),
                "totalDurationMs", result.totalDurationMs(),
                "stageOutputs", result.stageOutputs(),
                "failureReason", result.failureReason() == null ? "" : result.failureReason(),
                "startedAt", started.toString()));
    }

    // ── helpers ─────────────────────────────────────

    public record TestRunRequest(String input, Long timeoutSeconds) {}

    private static ValidationReport combine(ValidationReport a, ValidationReport b) {
        if (a == null) return b == null ? new ValidationReport(Map.of(), List.of()) : b;
        if (b == null) return a;
        ValidationReport.Builder builder = new ValidationReport.Builder();
        a.byPipeline().forEach((pid, errs) -> errs.forEach(err ->
                builder.addPipelineError(pid, err)));
        a.global().forEach(builder::addGlobalError);
        b.byPipeline().forEach((pid, errs) -> errs.forEach(err ->
                builder.addPipelineError(pid, err)));
        b.global().forEach(builder::addGlobalError);
        return builder.build();
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
