package io.jaiclaw.audit.mcp;

import io.jaiclaw.audit.TranscriptSearchResult;
import io.jaiclaw.audit.TranscriptStore;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Exposes {@link TranscriptStore#search} as the MCP tool
 * {@code sessions_search} so agents can look up their own past
 * conversations mid-session ("did we discuss X before?").
 *
 * <p>The tool is scoped to the caller's tenant — searches never cross
 * tenant boundaries. In SINGLE-mode deployments where the caller has no
 * {@link TenantContext}, the search runs against the default-tenant
 * partition.
 *
 * <p>Wire format:
 * <pre>{@code
 * {
 *   "query": "kubernetes rollout",
 *   "limit": 5
 * }
 * }</pre>
 *
 * <p>Returns a plain-text summary of matches — session id, timestamp,
 * channel, and the matched utterance snippet. Agents get a compact
 * human-readable result they can quote back to the user; if they want
 * the full transcript they can call {@code sessions_load} (future
 * companion tool) with the returned id.
 */
public class SessionSearchMcpToolProvider implements McpToolProvider {

    private static final String SERVER_NAME = "sessions";
    private static final String TOOL_NAME = "sessions_search";
    private static final int DEFAULT_LIMIT = 10;
    private static final int SNIPPET_MAX = 240;

    private final TranscriptStore store;

    public SessionSearchMcpToolProvider(TranscriptStore store) {
        if (store == null) throw new IllegalArgumentException("store must not be null");
        this.store = store;
    }

    @Override
    public String getServerName() {
        return SERVER_NAME;
    }

    @Override
    public String getServerDescription() {
        return "Full-text search over stored session transcripts. Scoped to the "
                + "caller's tenant. Use to find prior conversations that mentioned "
                + "a topic before answering the user.";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(new McpToolDefinition(
                TOOL_NAME,
                "Search stored transcripts for utterances containing a substring. "
                        + "Returns most-recent-session matches first, one match per session. "
                        + "Scoped to the caller's tenant.",
                """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "Case-insensitive substring to search for."
                    },
                    "limit": {
                      "type": "integer",
                      "description": "Max matches to return (default 10, max 50).",
                      "minimum": 1,
                      "maximum": 50
                    }
                  },
                  "required": ["query"]
                }
                """));
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (!TOOL_NAME.equals(toolName)) {
            return McpToolResult.error("Unknown tool: " + toolName);
        }
        Object rawQuery = args.get("query");
        if (!(rawQuery instanceof String query) || query.isBlank()) {
            return McpToolResult.error("'query' is required and must be a non-blank string");
        }
        int limit = DEFAULT_LIMIT;
        Object rawLimit = args.get("limit");
        if (rawLimit instanceof Number n) {
            limit = Math.max(1, Math.min(50, n.intValue()));
        }
        String tenantId = tenant != null ? tenant.getTenantId() : null;

        List<TranscriptSearchResult> results = store.search(query, tenantId, limit);
        return McpToolResult.success(format(query, results));
    }

    private static String format(String query, List<TranscriptSearchResult> results) {
        if (results.isEmpty()) {
            return "No transcript matches for query: \"" + query + "\"";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size())
                .append(" session(s) mentioning \"").append(query).append("\":\n\n");
        for (TranscriptSearchResult r : results) {
            sb.append("• session=").append(r.sessionId());
            if (r.startTime() != null) {
                sb.append("  at=").append(DateTimeFormatter.ISO_INSTANT.format(r.startTime()));
            }
            if (r.channel() != null) sb.append("  channel=").append(r.channel());
            if (r.agentId() != null) sb.append("  agent=").append(r.agentId());
            sb.append('\n');
            if (r.matchedUtterance() != null) {
                String content = r.matchedUtterance().content();
                if (content != null && !content.isBlank()) {
                    if (content.length() > SNIPPET_MAX) {
                        content = content.substring(0, SNIPPET_MAX) + "…";
                    }
                    sb.append("  ").append(r.matchedUtterance().role())
                            .append(": ").append(content).append('\n');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
