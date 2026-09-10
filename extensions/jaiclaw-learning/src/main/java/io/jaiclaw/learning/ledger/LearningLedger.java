package io.jaiclaw.learning.ledger;

import io.jaiclaw.core.api.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only JSONL record of every learned-skill mutation, paired with a
 * {@link BlobStore} holding the exact before/after bodies.
 *
 * <p>Append-only and never rewritten: the value of a ledger is that it cannot be
 * quietly edited after the fact. A rollback is itself recorded as a new entry
 * rather than removing the entry it reverses.
 *
 * <p>{@link #contentFor(String, String)} <strong>fails closed</strong> — if a
 * blob referenced by an entry is missing, rollback refuses rather than restoring
 * partial or wrong content.
 *
 * <p>Layout: {@code {base}/{tenant}/ledger/entries.jsonl} and
 * {@code {base}/{tenant}/ledger/blobs/}.
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public class LearningLedger {

    private static final Logger log = LoggerFactory.getLogger(LearningLedger.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Path baseDir;

    public LearningLedger(Path baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Records a mutation, storing both bodies as blobs first so an entry can
     * never reference content that was not written.
     *
     * @param before body before the change, or null when creating
     * @param after  body after the change
     */
    public LedgerEntry record(String tenantId, String proposalId, String skillName,
                              String action, String before, String after, String actor) {
        BlobStore blobs = blobs(tenantId);
        String beforeHash = before == null ? null : blobs.put(before);
        String afterHash = after == null ? null : blobs.put(after);

        LedgerEntry entry = new LedgerEntry(UUID.randomUUID().toString(), tenantId, proposalId,
                skillName, action, beforeHash, afterHash, actor, Instant.now());
        append(tenantId, entry);
        return entry;
    }

    /** All entries for a tenant, oldest first. */
    public List<LedgerEntry> entries(String tenantId) {
        Path file = ledgerFile(tenantId);
        if (!Files.exists(file)) return List.of();
        List<LedgerEntry> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    out.add(fromJson(MAPPER.readTree(line)));
                } catch (Exception e) {
                    // Skip an unreadable line rather than losing the whole history.
                    log.warn("Skipping unreadable ledger line for tenant {}", tenantId);
                }
            }
        } catch (IOException e) {
            log.warn("Could not read ledger for tenant {}", tenantId, e);
            return List.of();
        }
        return List.copyOf(out);
    }

    /** Entries affecting one skill, oldest first. */
    public List<LedgerEntry> entriesForSkill(String tenantId, String skillName) {
        return entries(tenantId).stream()
                .filter(e -> e.skillName().equals(skillName))
                .toList();
    }

    public Optional<LedgerEntry> find(String tenantId, String entryId) {
        return entries(tenantId).stream()
                .filter(e -> e.entryId().equals(entryId))
                .findFirst();
    }

    /**
     * The content behind a blob hash.
     *
     * @return empty when the hash is null or the blob is gone — callers must
     *         treat empty as "cannot roll back", never as "restore nothing"
     */
    public Optional<String> contentFor(String tenantId, String hash) {
        if (hash == null) return Optional.empty();
        return blobs(tenantId).get(hash);
    }

    public BlobStore blobs(String tenantId) {
        return new BlobStore(ledgerDir(tenantId).resolve("blobs"));
    }

    private void append(String tenantId, LedgerEntry entry) {
        Path file = ledgerFile(tenantId);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writeValueAsString(toJson(entry)) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append ledger entry for " + tenantId, e);
        }
    }

    private Path ledgerDir(String tenantId) {
        return baseDir.resolve(io.jaiclaw.learning.util.PathSegments.safe(tenantId))
                .resolve("ledger");
    }

    private Path ledgerFile(String tenantId) {
        return ledgerDir(tenantId).resolve("entries.jsonl");
    }

    private ObjectNode toJson(LedgerEntry e) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("entryId", e.entryId());
        n.put("tenantId", e.tenantId());
        n.put("proposalId", e.proposalId());
        n.put("skillName", e.skillName());
        n.put("action", e.action());
        n.put("beforeHash", e.beforeHash());
        n.put("afterHash", e.afterHash());
        n.put("actor", e.actor());
        n.put("at", e.at().toString());
        return n;
    }

    private LedgerEntry fromJson(JsonNode n) {
        return new LedgerEntry(
                text(n, "entryId"), text(n, "tenantId"), text(n, "proposalId"),
                text(n, "skillName"), text(n, "action"),
                text(n, "beforeHash"), text(n, "afterHash"), text(n, "actor"),
                Instant.parse(text(n, "at")));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }
}
