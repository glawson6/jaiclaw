package io.jaiclaw.kanban.journal;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.kanban.events.TaskStateChanged;
import io.jaiclaw.kanban.model.TransitionRecord;
import io.jaiclaw.kanban.service.TransitionHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Append-only per-board JSONL log of accepted transitions. Phase 4 plan §9.
 *
 * <p>Layout: one file per board at
 * {@code {boards-dir}/../journal/{boardId}.jsonl}. On {@link SmartLifecycle#start}
 * each file's tail is replayed into {@link TransitionHistory} so the
 * bounded deque survives a restart. Subsequent transitions append a
 * single JSON line as soon as the {@link TaskStateChanged} event fires.
 *
 * <p>Append failures escalate: a kanban deployment that opted into a
 * durable journal expects it to be durable. Read failures (a single
 * malformed line) are logged and skipped — one bad line shouldn't lose
 * the rest of the history.
 */
public class TransitionJournal implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TransitionJournal.class);
    private static final String SUFFIX = ".jsonl";

    private final Path journalDir;
    private final TransitionHistory history;
    private final int replayLimit;
    private final ObjectMapper json;
    private final ConcurrentMap<String, Object> writeLocks = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private volatile boolean replayed = false;

    public TransitionJournal(Path journalDir, TransitionHistory history, int replayLimit) {
        this.journalDir = journalDir;
        this.history = history;
        this.replayLimit = Math.max(1, replayLimit);
        this.json = new ObjectMapper()
                
                ;
    }

    /**
     * Spring event listener — appends the accepted transition synchronously
     * on the publisher's thread. The transition is already in
     * {@link TransitionHistory}'s in-memory deque by the time we get
     * here (the service writes there before publishing), so the journal
     * is purely additive.
     */
    @EventListener
    public void onTaskStateChanged(TaskStateChanged event) {
        TransitionRecord record = event.transition();
        if (record == null || record.boardId() == null) return;
        appendInternal(record);
    }

    /** Visible for direct callers (recovery, tests). */
    public void append(TransitionRecord record) {
        appendInternal(record);
    }

    private void appendInternal(TransitionRecord record) {
        Path file = journalDir.resolve(record.boardId() + SUFFIX);
        Object lock = writeLocks.computeIfAbsent(record.boardId(), k -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(journalDir);
                String line = json.writeValueAsString(record) + "\n";
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND});
            } catch (IOException e) {
                log.error("Failed to append transition journal entry for board {}: {}",
                        record.boardId(), e.getMessage());
                throw new IllegalStateException("Transition journal append failed", e);
            }
        }
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        if (replayed) return;
        replayed = true;
        try {
            int boards = replayAll();
            log.info("Transition journal: replayed {} board journal(s) into TransitionHistory", boards);
        } catch (RuntimeException ex) {
            log.warn("Transition journal replay failed: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Run BEFORE the recovery manager so the deque is populated.
        return Integer.MAX_VALUE - 400;
    }

    /** Visible for tests. Returns the number of boards replayed. */
    public int replayAll() {
        if (!Files.exists(journalDir)) return 0;
        int boards = 0;
        try (Stream<Path> stream = Files.list(journalDir)) {
            List<Path> files = stream
                    .filter(p -> p.getFileName().toString().endsWith(SUFFIX))
                    .toList();
            for (Path file : files) {
                String boardId = stem(file.getFileName().toString());
                replayOne(boardId, file);
                boards++;
            }
        } catch (IOException e) {
            log.warn("Failed to list journal dir {}: {}", journalDir, e.getMessage());
        }
        return boards;
    }

    private void replayOne(String boardId, Path file) {
        // Read every line, parse what we can, sort by timestamp ascending,
        // keep only the last `replayLimit` so we match the deque bound.
        List<TransitionRecord> parsed = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    parsed.add(json.readValue(line, new TypeReference<TransitionRecord>() {}));
                } catch (Exception badLine) {
                    log.warn("Skipping malformed journal line in {}: {}",
                            file, badLine.getMessage());
                }
            }
        } catch (IOException ioe) {
            log.warn("Failed to read journal {}: {}", file, ioe.getMessage());
            return;
        }
        parsed.sort(Comparator.comparing(TransitionRecord::timestamp));
        int from = Math.max(0, parsed.size() - replayLimit);
        for (int i = from; i < parsed.size(); i++) {
            history.record(parsed.get(i));
        }
        log.debug("Replayed {} transitions into history for board {}",
                parsed.size() - from, boardId);
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
