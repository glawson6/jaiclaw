package io.jaiclaw.kanban;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;

/**
 * Configuration surface for the kanban extension. Mirrors analysis §7.
 *
 * <p>Several sub-records reserve fields that Phase 3 / Phase 4 populate
 * (recovery, processors, journal); Phase 1 honours {@link #enabled()},
 * {@link #boardsDir()}, {@link Locations#patterns()},
 * {@link History#maxPerBoard()}; Phase 2 adds {@link Boards#writable()}.
 */
@ConfigurationProperties(prefix = "jaiclaw.kanban")
public record KanbanProperties(
        boolean enabled,
        String boardsDir,
        Engine engine,
        Locations locations,
        Http http,
        Sse sse,
        History history,
        Recovery recovery,
        Processors processors,
        Boards boards
) {
    @ConstructorBinding
    public KanbanProperties {
        if (boardsDir == null || boardsDir.isBlank()) {
            boardsDir = System.getProperty("user.home") + "/.jaiclaw/kanban/boards";
        }
        if (engine == null) engine = new Engine("graph");
        if (locations == null) locations = new Locations(List.of());
        if (http == null) http = new Http(true, "/api/kanban");
        if (sse == null) sse = new Sse(true, 25, 100);
        if (history == null) history = new History(200, false);
        if (recovery == null) recovery = new Recovery(true, "fail", 3, "30m");
        if (processors == null) processors = new Processors(true, 5);
        if (boards == null) boards = new Boards(true);
    }

    /**
     * Legacy 9-arg constructor — Phase 1 callers (tests, embeddings)
     * keep compiling unchanged with default board-writability ({@code true}).
     */
    public KanbanProperties(boolean enabled, String boardsDir, Engine engine,
                            Locations locations, Http http, Sse sse, History history,
                            Recovery recovery, Processors processors) {
        this(enabled, boardsDir, engine, locations, http, sse, history, recovery, processors, null);
    }

    public record Engine(String name) {
        public Engine {
            if (name == null || name.isBlank()) name = "graph";
        }
    }

    public record Locations(List<String> patterns) {
        public Locations {
            patterns = patterns == null ? List.of() : List.copyOf(patterns);
        }
    }

    public record Http(boolean enabled, String basePath) {
        public Http {
            if (basePath == null || basePath.isBlank()) basePath = "/api/kanban";
        }
    }

    public record Sse(boolean enabled, int heartbeatSeconds, int maxConnections) {
        public Sse {
            if (heartbeatSeconds <= 0) heartbeatSeconds = 25;
            if (maxConnections <= 0) maxConnections = 100;
        }
    }

    public record History(int maxPerBoard, boolean journal) {
        public History {
            if (maxPerBoard <= 0) maxPerBoard = 200;
        }
    }

    public record Recovery(boolean enabled, String defaultRestartPolicy,
                           int maxAttempts, String staleRunningTimeout) {
        public Recovery {
            if (defaultRestartPolicy == null || defaultRestartPolicy.isBlank())
                defaultRestartPolicy = "fail";
            if (maxAttempts <= 0) maxAttempts = 3;
            if (staleRunningTimeout == null || staleRunningTimeout.isBlank())
                staleRunningTimeout = "30m";
        }
    }

    public record Processors(boolean enabled, int maxConcurrent) {
        public Processors {
            if (maxConcurrent <= 0) maxConcurrent = 5;
        }
    }

    /**
     * Board-level controls.
     *
     * @param writable when {@code false}, {@code POST /api/kanban/boards}
     *                 and {@code DELETE /api/kanban/boards/{id}} return
     *                 {@code 405 Method Not Allowed} — useful for prod
     *                 deployments where boards ship via git/CI/Helm only.
     */
    public record Boards(boolean writable) {
    }
}
