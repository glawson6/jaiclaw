package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Filesystem {@link ProposalStore}: one JSON file per proposal, under a
 * per-tenant directory.
 *
 * <pre>
 *   {baseDir}/{tenantId}/proposals/{id}.json
 * </pre>
 *
 * <p>One file per proposal rather than one document per tenant, so two
 * concurrent reviewers writing different proposals never contend, and a single
 * unreadable file costs one proposal instead of the whole queue.
 *
 * <p>Writes are atomic (temp file + {@link StandardCopyOption#ATOMIC_MOVE}),
 * matching {@code JsonFileTaskStore}. An unreadable file is quarantined to
 * {@code .corrupt-<epochMillis>} and skipped — unlike the task store this does
 * <em>not</em> fail startup, because a damaged suggestion must never stop the
 * agent from serving traffic.
 *
 * <p>Tenant ids are sanitised before use as a path segment, so a hostile or
 * malformed tenant id cannot escape the base directory.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public class JsonFileProposalStore implements ProposalStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileProposalStore.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Path baseDir;

    public JsonFileProposalStore(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void save(Proposal proposal) {
        if (proposal == null) return;
        Path dir = proposalsDir(proposal.tenantId());
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(safeSegment(proposal.id()) + ".json");
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, MAPPER.writeValueAsString(toJson(proposal)), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save proposal " + proposal.id(), e);
        }
    }

    @Override
    public Optional<Proposal> find(String tenantId, String id) {
        if (id == null) return Optional.empty();
        Path file = proposalsDir(tenantId).resolve(safeSegment(id) + ".json");
        return Files.exists(file) ? read(file) : Optional.empty();
    }

    @Override
    public List<Proposal> list(String tenantId) {
        Path dir = proposalsDir(tenantId);
        if (!Files.isDirectory(dir)) return List.of();
        List<Proposal> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> read(p).ifPresent(out::add));
        } catch (IOException e) {
            log.warn("Could not list proposals for tenant {}", tenantId, e);
            return List.of();
        }
        out.sort(Comparator.comparing(Proposal::createdAt).reversed());
        return List.copyOf(out);
    }

    @Override
    public List<Proposal> list(String tenantId, ProposalState state) {
        if (state == null) return list(tenantId);
        return list(tenantId).stream().filter(p -> p.state() == state).toList();
    }

    @Override
    public boolean existsByContentHash(String tenantId, String contentHash) {
        if (contentHash == null) return false;
        // A rejected proposal does not block a later identical one: an operator
        // may reject something now and want it offered again after the situation
        // changes. Applied and pending ones do block.
        return list(tenantId).stream()
                .filter(p -> p.state() != ProposalState.REJECTED)
                .anyMatch(p -> contentHash.equals(p.contentHash()));
    }

    @Override
    public boolean delete(String tenantId, String id) {
        if (id == null) return false;
        try {
            return Files.deleteIfExists(proposalsDir(tenantId).resolve(safeSegment(id) + ".json"));
        } catch (IOException e) {
            log.warn("Could not delete proposal {} for tenant {}", id, tenantId, e);
            return false;
        }
    }

    /** The directory this tenant's proposals live in. */
    public Path proposalsDir(String tenantId) {
        return baseDir.resolve(safeSegment(tenantId == null ? "default" : tenantId))
                .resolve("proposals");
    }

    private Optional<Proposal> read(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.ofNullable(fromJson(MAPPER.readTree(json)));
        } catch (Exception e) {
            quarantine(file, e);
            return Optional.empty();
        }
    }

    /**
     * Moves an unreadable file aside so it is not re-read on every list, and logs
     * loudly. Deliberately non-fatal: a corrupt suggestion must not stop the agent.
     */
    private void quarantine(Path file, Exception cause) {
        Path target = file.resolveSibling(
                file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);
            log.error("Proposal file {} was unreadable; quarantined to {}", file, target, cause);
        } catch (IOException moveFailed) {
            log.error("Proposal file {} was unreadable and could not be quarantined", file, cause);
        }
    }

    // ─── Serialization ────────────────────────────────────────────────────────
    // Hand-rolled rather than polymorphic Jackson binding: the sealed hierarchy
    // is small, and an explicit "kind" discriminator keeps the on-disk format
    // readable by an operator and stable against refactors of the Java types.

    private ObjectNode toJson(Proposal p) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("kind", p.kind().name());
        node.put("id", p.id());
        node.put("tenantId", p.tenantId());
        node.put("originSessionKey", p.originSessionKey());
        node.put("createdAt", p.createdAt().toString());
        node.put("state", p.state().name());
        node.put("summary", p.summary());
        node.put("contentHash", p.contentHash());
        switch (p) {
            case MemoryProposal m -> {
                node.put("scope", m.scope().name());
                node.put("heading", m.heading());
                node.put("content", m.content());
            }
            case SkillProposal s -> {
                node.put("skillName", s.skillName());
                node.put("description", s.description());
                node.put("body", s.body());
            }
            case SkillPatchProposal s -> {
                node.put("skillName", s.skillName());
                node.put("findText", s.findText());
                node.put("replaceText", s.replaceText());
            }
        }
        return node;
    }

    private Proposal fromJson(tools.jackson.databind.JsonNode n) {
        if (n == null || !n.has("kind")) return null;
        String id = text(n, "id");
        String tenantId = text(n, "tenantId");
        String origin = text(n, "originSessionKey");
        Instant createdAt = Instant.parse(text(n, "createdAt"));
        ProposalState state = ProposalState.valueOf(text(n, "state"));
        String summary = text(n, "summary");

        return switch (ProposalKind.valueOf(text(n, "kind"))) {
            case MEMORY -> new MemoryProposal(id, tenantId, origin, createdAt, state, summary,
                    io.jaiclaw.core.model.MemoryScope.valueOf(text(n, "scope")),
                    text(n, "heading"), text(n, "content"));
            case SKILL -> new SkillProposal(id, tenantId, origin, createdAt, state, summary,
                    text(n, "skillName"), text(n, "description"), text(n, "body"));
            case SKILL_PATCH -> new SkillPatchProposal(id, tenantId, origin, createdAt, state, summary,
                    text(n, "skillName"), text(n, "findText"), text(n, "replaceText"));
        };
    }

    private static String text(tools.jackson.databind.JsonNode n, String field) {
        var v = n.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    /** Delegates to the shared traversal defence; see {@link io.jaiclaw.learning.util.PathSegments}. */
    static String safeSegment(String raw) {
        return io.jaiclaw.learning.util.PathSegments.safe(raw);
    }
}
