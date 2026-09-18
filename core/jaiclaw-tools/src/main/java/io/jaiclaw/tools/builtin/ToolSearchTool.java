package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolProfileHolder;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.search.SessionToolDiscoveries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code tool_search} — finds tools whose schemas were withheld from the prompt.
 *
 * <p>With a large catalog (MCP servers, Camel endpoints), sending every schema on
 * every request wastes a great deal of context. Deferred tools are omitted from
 * the prompt; this tool surfaces them on demand and <em>admits</em> what it
 * returns for the rest of the session, so a discovered tool is directly callable
 * from the next turn onward.
 *
 * <p>Registered only when {@code jaiclaw.tools.search.enabled=true}.
 *
 * <p>Phase 3 of the 1.2.0 plan.
 */
public class ToolSearchTool extends AbstractBuiltinTool {

    /** Section tag, so the search surface can be excluded by policy. */
    public static final String SECTION = "meta";

    /** Default number of results when the caller does not ask for a specific count. */
    public static final int DEFAULT_LIMIT = 5;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "What you need to do, in plain words — e.g. 'read a file', 'list kubernetes pods', 'send a slack message'."
                },
                "limit": {
                  "type": "integer",
                  "description": "Maximum tools to return (default 5)."
                }
              },
              "required": ["query"]
            }
            """;

    private final ToolRegistry registry;
    private final SessionToolDiscoveries discoveries;
    private final int defaultLimit;

    public ToolSearchTool(ToolRegistry registry, SessionToolDiscoveries discoveries) {
        this(registry, discoveries, DEFAULT_LIMIT);
    }

    public ToolSearchTool(ToolRegistry registry, SessionToolDiscoveries discoveries, int defaultLimit) {
        super(new ToolDefinition(
                "tool_search",
                "Find tools that are available to you but whose details were not included up front. "
                        + "Search before concluding a capability is missing — many tools are only "
                        + "listed on demand. Tools returned here become directly callable.",
                SECTION,
                SCHEMA,
                Set.of(ToolProfile.values())));
        this.registry = registry;
        this.discoveries = discoveries;
        this.defaultLimit = defaultLimit > 0 ? defaultLimit : DEFAULT_LIMIT;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String query = optionalParam(parameters, "query", null);
        if (query == null || query.isBlank()) {
            return new ToolResult.Error("'query' is required — describe what you are trying to do.");
        }
        int limit = parseLimit(parameters.get("limit"));

        // The run's profile bounds what search may reveal: a deferred tool the
        // profile does not permit must stay invisible, or search would become a
        // way to enumerate tools the run is not allowed to call.
        //
        // Fails CLOSED. This executes inside an agent turn, by which point
        // GatewayService has always set the holder; an unset profile here means
        // something went wrong upstream, and the safe answer to "which tools
        // exist" is then "none you may call" rather than "all of them".
        ToolProfile profile = ToolProfileHolder.getOrDefault(ToolProfile.MINIMAL);
        List<ToolDefinition> hits = registry.search(query, profile, limit);

        if (hits.isEmpty()) {
            return new ToolResult.Success("""
                    {"query":%s,"count":0,"tools":[],"note":"No matching tools. Try different words, \
                    or proceed without one and say what you could not do."}"""
                    .formatted(quote(query)));
        }

        // Admit the results for the rest of the session so they are callable next turn.
        Set<String> names = new LinkedHashSet<>();
        for (ToolDefinition d : hits) names.add(d.name());
        if (context != null) discoveries.discover(context.sessionKey(), names);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"query\":").append(quote(query))
                .append(",\"count\":").append(hits.size())
                .append(",\"tools\":[");
        for (int i = 0; i < hits.size(); i++) {
            ToolDefinition d = hits.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":").append(quote(d.name()))
                    .append(",\"description\":").append(quote(d.description()))
                    .append(",\"section\":").append(quote(d.section()))
                    .append(",\"input_schema\":").append(quote(d.inputSchema()))
                    .append('}');
        }
        sb.append("],\"note\":\"These tools are now available to call directly.\"}");
        return new ToolResult.Success(sb.toString());
    }

    private int parseLimit(Object raw) {
        if (raw == null) return defaultLimit;
        int value;
        if (raw instanceof Number n) {
            value = n.intValue();
        } else {
            try {
                value = Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException e) {
                return defaultLimit;
            }
        }
        if (value <= 0) return defaultLimit;
        // Cap so one search cannot undo the context saving deferral bought.
        return Math.min(value, 25);
    }

    static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
