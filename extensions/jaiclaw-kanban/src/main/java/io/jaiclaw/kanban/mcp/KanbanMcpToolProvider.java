package io.jaiclaw.kanban.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.BoardSnapshot;
import io.jaiclaw.kanban.render.AsciiBoardOptions;
import io.jaiclaw.kanban.render.BoardAsciiRenderer;
import io.jaiclaw.kanban.service.BoardSnapshotService;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.kanban.state.TransitionResult;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MCP tool provider for the kanban extension. Exposes the same five
 * operations as the agent-side tools (so an external MCP client and
 * the JaiClaw agent share one vocabulary):
 *
 * <ul>
 *   <li>{@code board_list} — boards visible to the tenant</li>
 *   <li>{@code board_show} — snapshot of one board as JSON</li>
 *   <li>{@code board_ascii} — ASCII rendering of one board</li>
 *   <li>{@code task_move} — fire a transition event on a card</li>
 *   <li>{@code task_claim} — set the assignee on a card</li>
 * </ul>
 *
 * <p>Server name: {@code kanban}. The provider forwards the incoming
 * {@link TenantContext} into downstream services through the existing
 * {@code TenantContextHolder} contract — every tool here respects
 * tenant isolation via the standard {@link KanbanBoardService} +
 * {@link TaskStore} surfaces.
 */
public class KanbanMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(KanbanMcpToolProvider.class);
    private static final String SERVER_NAME = "kanban";
    private static final String SERVER_DESCRIPTION =
            "Kanban boards — list, snapshot, ASCII render, move and claim cards";

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final KanbanBoardService boardService;
    private final BoardSnapshotService snapshotService;
    private final TaskTransitionService transitionService;
    private final TaskStore taskStore;
    private final BoardAsciiRenderer renderer;

    public KanbanMcpToolProvider(KanbanBoardService boardService,
                                 BoardSnapshotService snapshotService,
                                 TaskTransitionService transitionService,
                                 TaskStore taskStore,
                                 BoardAsciiRenderer renderer) {
        this.boardService = boardService;
        this.snapshotService = snapshotService;
        this.transitionService = transitionService;
        this.taskStore = taskStore;
        this.renderer = renderer;
    }

    @Override public String getServerName() { return SERVER_NAME; }

    @Override public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("board_list",
                        "List kanban boards visible to the current tenant",
                        BOARD_LIST_SCHEMA),
                new McpToolDefinition("board_show",
                        "Snapshot of one kanban board (columns + cards) as JSON",
                        BOARD_SHOW_SCHEMA),
                new McpToolDefinition("board_ascii",
                        "ASCII rendering of one kanban board (full or compact)",
                        BOARD_ASCII_SCHEMA),
                new McpToolDefinition("task_move",
                        "Fire a transition event on a kanban card",
                        TASK_MOVE_SCHEMA),
                new McpToolDefinition("task_claim",
                        "Set the assignee on a kanban card",
                        TASK_CLAIM_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        try {
            return switch (toolName) {
                case "board_list"  -> handleBoardList(args);
                case "board_show"  -> handleBoardShow(args);
                case "board_ascii" -> handleBoardAscii(args);
                case "task_move"   -> handleTaskMove(args);
                case "task_claim"  -> handleTaskClaim(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    // ── handlers ────────────────────────────────────────────────────

    private McpToolResult handleBoardList(Map<String, Object> args) throws JsonProcessingException {
        List<Map<String, Object>> summaries = boardService.list().stream()
                .map(this::boardSummary)
                .collect(Collectors.toList());
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("count", summaries.size());
        payload.put("boards", summaries);
        return McpToolResult.success(JSON.writeValueAsString(payload));
    }

    private Map<String, Object> boardSummary(BoardDefinition b) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", b.id());
        m.put("name", b.name());
        m.put("initialState", b.initialState());
        m.put("columnCount", b.columns().size());
        m.put("transitionCount", b.transitions().size());
        return m;
    }

    private McpToolResult handleBoardShow(Map<String, Object> args) throws JsonProcessingException {
        String boardId = requireString(args, "boardId");
        Optional<BoardSnapshot> snapshot = snapshotService.snapshot(boardId);
        if (snapshot.isEmpty()) {
            return McpToolResult.error("Board not found: " + boardId);
        }
        return McpToolResult.success(JSON.writeValueAsString(snapshot.get()));
    }

    private McpToolResult handleBoardAscii(Map<String, Object> args) {
        String boardId = requireString(args, "boardId");
        int width = intOrDefault(args.get("width"), 120);
        String styleStr = stringOrDefault(args, "style", "full");
        Optional<BoardSnapshot> snapshot = snapshotService.snapshot(boardId);
        if (snapshot.isEmpty()) {
            return McpToolResult.error("Board not found: " + boardId);
        }
        AsciiBoardOptions.Style style = "compact".equalsIgnoreCase(styleStr)
                ? AsciiBoardOptions.Style.COMPACT
                : AsciiBoardOptions.Style.FULL;
        AsciiBoardOptions options = new AsciiBoardOptions(width, style, 2, true, "(empty)");
        return McpToolResult.success(renderer.render(snapshot.get(), options));
    }

    private McpToolResult handleTaskMove(Map<String, Object> args) throws JsonProcessingException {
        String taskId = requireString(args, "taskId");
        String event = requireString(args, "event");
        String actor = stringOrDefault(args, "actor", null);
        try {
            TransitionResult result = transitionService.transition(taskId, event, actor);
            if (!result.accepted()) {
                return McpToolResult.error(
                        "Transition rejected: " + result.reason()
                                + " (current state: " + result.fromState() + ")");
            }
            return McpToolResult.success(JSON.writeValueAsString(Map.of(
                    "accepted", true,
                    "fromState", result.fromState(),
                    "toState", result.toState(),
                    "event", event)));
        } catch (IllegalArgumentException ex) {
            return McpToolResult.error(ex.getMessage());
        }
    }

    private McpToolResult handleTaskClaim(Map<String, Object> args) throws JsonProcessingException {
        String taskId = requireString(args, "taskId");
        String assignee = stringOrDefault(args, "assignee", null);
        if (assignee != null && assignee.isBlank()) assignee = null;
        Optional<TaskRecord> existing = taskStore.findById(taskId);
        if (existing.isEmpty()) {
            return McpToolResult.error("Task not found: " + taskId);
        }
        Optional<TaskRecord> persisted = taskStore.compareAndSave(existing.get().withAssignee(assignee));
        if (persisted.isEmpty()) {
            return McpToolResult.error(
                    "Concurrent modification on task " + taskId + " — re-read and retry");
        }
        return McpToolResult.success(JSON.writeValueAsString(Map.of(
                "taskId", taskId,
                "assignee", persisted.get().assignee() == null ? "" : persisted.get().assignee(),
                "version", persisted.get().version())));
    }

    // ── helpers ─────────────────────────────────────────────────────

    private static String requireString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return v.toString();
    }

    private static String stringOrDefault(Map<String, Object> args, String key, String fallback) {
        Object v = args.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : fallback;
    }

    private static int intOrDefault(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.intValue();
        try { return Integer.parseInt(raw.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ── JSON schemas ────────────────────────────────────────────────

    private static final String BOARD_LIST_SCHEMA = """
            {"type":"object","properties":{}}""";

    private static final String BOARD_SHOW_SCHEMA = """
            {"type":"object","properties":{
                "boardId":{"type":"string","description":"Board id"}
            },"required":["boardId"]}""";

    private static final String BOARD_ASCII_SCHEMA = """
            {"type":"object","properties":{
                "boardId":{"type":"string","description":"Board id"},
                "width":{"type":"integer","description":"Canvas width (default 120)"},
                "style":{"type":"string","description":"full or compact (default full)"}
            },"required":["boardId"]}""";

    private static final String TASK_MOVE_SCHEMA = """
            {"type":"object","properties":{
                "taskId":{"type":"string"},
                "event":{"type":"string","description":"Transition event name"},
                "actor":{"type":"string","description":"Optional actor for the audit record"}
            },"required":["taskId","event"]}""";

    private static final String TASK_CLAIM_SCHEMA = """
            {"type":"object","properties":{
                "taskId":{"type":"string"},
                "assignee":{"type":"string","description":"Assignee handle, or empty to unassign"}
            },"required":["taskId"]}""";
}
