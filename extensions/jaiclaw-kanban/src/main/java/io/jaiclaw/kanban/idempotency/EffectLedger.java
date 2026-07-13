package io.jaiclaw.kanban.idempotency;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@code {idempotencyKey → persisted result}} ledger backing the
 * compute-dedupe path in analysis §6.8. Lives under
 * {@code {storage-dir}/effects.jsonl} as a single append-only journal
 * — one JSON line per recorded effect — so a crash mid-write cannot
 * leave a half-record visible to the next read.
 *
 * <p>The in-memory map is rehydrated from the file on construction.
 * Phase 4 will move this alongside the transition journal at
 * {@code {boards-dir}/../journal/{boardId}.effects.jsonl}; Phase 3
 * keeps things simple with one file per process.
 */
public class EffectLedger {

    private static final Logger log = LoggerFactory.getLogger(EffectLedger.class);

    private final Path journalPath;
    private final ObjectMapper json;
    private final ConcurrentMap<String, String> entries = new ConcurrentHashMap<>();

    public EffectLedger(Path storageDir) {
        this.journalPath = storageDir.resolve("effects.jsonl");
        this.json = new ObjectMapper()
                
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create effects journal dir: " + storageDir, e);
        }
        loadFromDisk();
    }

    /** Returns the persisted result for {@code key}, or empty if no record. */
    public Optional<String> lookup(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(entries.get(key));
    }

    /**
     * Record the result of a completed processor execution. Subsequent
     * {@link #lookup} on the same key returns the same value, even after
     * a process restart. Idempotent: re-recording the same {@code key} +
     * {@code result} is a no-op.
     */
    public synchronized void record(String key, String result) {
        if (key == null) return;
        String prior = entries.put(key, result == null ? "" : result);
        if (prior != null && prior.equals(result)) {
            return;
        }
        try {
            String line = json.writeValueAsString(new Entry(key, result, Instant.now()));
            Files.writeString(journalPath, line + "\n", StandardCharsets.UTF_8,
                    new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND});
        } catch (IOException e) {
            log.error("Failed to append effect-ledger entry for {}: {}", key, e.getMessage());
            throw new IllegalStateException("Effect ledger append failed", e);
        }
    }

    public int size() {
        return entries.size();
    }

    private void loadFromDisk() {
        if (!Files.exists(journalPath)) return;
        try {
            for (String line : Files.readAllLines(journalPath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    Entry entry = json.readValue(line, Entry.class);
                    if (entry.key() != null) {
                        entries.put(entry.key(), entry.result() == null ? "" : entry.result());
                    }
                } catch (IOException parseError) {
                    log.warn("Skipping malformed effect-ledger line: {}", parseError.getMessage());
                }
            }
            log.info("Loaded {} effect-ledger entries from {}", entries.size(), journalPath);
        } catch (IOException e) {
            log.warn("Failed to read effect ledger at {}: {}", journalPath, e.getMessage());
        }
    }

    /** Append-only journal record. */
    private record Entry(String key, String result, Instant recordedAt) {}
}
