package io.jaiclaw.copilot.tool;

import com.github.copilot.generated.AssistantMessageToolRequest;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Bidirectional bridge between Spring AI's tool model and the GitHub Copilot
 * SDK's tool model.
 *
 * <p>Three responsibilities:
 *
 * <ol>
 *   <li><b>Outbound (Spring AI → Copilot).</b> Wraps each Spring AI
 *       {@link ToolCallback} as a Copilot {@link ToolDefinition} whose
 *       {@code ToolHandler} lambda delegates back into the Spring AI callback.
 *       This works because Copilot runs the tool handler <b>in-process</b>
 *       when its server invokes the tool — the SDK owns the call/response
 *       plumbing and only calls our lambda for the actual work.</li>
 *
 *   <li><b>Inbound event → Spring AI ToolCall.</b> Converts a Copilot
 *       {@link AssistantMessageToolRequest} into Spring AI's
 *       {@link AssistantMessage.ToolCall} shape. Used when the emitted tool
 *       call originated from a server-side or MCP-hosted tool (i.e. one that
 *       we did NOT register via outbound wrapping) and Spring AI callers want
 *       to see the assistant message with its tool-call intent.</li>
 *
 *   <li><b>JSON-Schema translation.</b> Spring AI's
 *       {@link org.springframework.ai.tool.definition.ToolDefinition#inputSchema()}
 *       returns a JSON-Schema <b>string</b>; Copilot's SDK expects the
 *       parameters as a Java {@link Map} shaped like a JSON Schema object.
 *       The mapper handles the parse/serialize.</li>
 * </ol>
 *
 * <p>Failure mode: if the incoming JSON Schema string doesn't parse as a
 * JSON object, the outbound conversion falls back to an empty parameters map
 * and logs at WARN. Copilot will accept the tool with no parameters — the
 * assistant just won't know how to call it. Prefer that soft-fail over
 * blowing up the entire {@code call()} attempt.
 */
public class CopilotToolMapper {

    private static final Logger log = LoggerFactory.getLogger(CopilotToolMapper.class);

    private final tools.jackson.databind.ObjectMapper json;

    /**
     * Uses a private Jackson 3 ObjectMapper — Copilot's SDK ships Jackson 2,
     * but our JSON-Schema string parse/serialize stays inside this module and
     * doesn't cross the SDK boundary (the SDK's own Map<String,Object> works
     * fine either way). We deliberately do NOT reuse the platform's
     * ObjectMapper bean here — the schema JSON has no application semantics
     * and needs no custom modules.
     */
    public CopilotToolMapper() {
        this.json = new tools.jackson.databind.ObjectMapper();
    }

    /**
     * Wraps a list of Spring AI {@link ToolCallback}s as Copilot
     * {@link ToolDefinition}s. The returned list is safe to pass directly to
     * {@code SessionConfig.setTools(...)}.
     *
     * @param callbacks Spring AI tool callbacks; empty or null → empty list
     * @return Copilot ToolDefinitions with in-process handlers delegating to
     *         the Spring AI callbacks
     */
    public List<ToolDefinition> toCopilotTools(List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolDefinition> out = new ArrayList<>(callbacks.size());
        for (ToolCallback cb : callbacks) {
            out.add(toCopilotTool(cb));
        }
        return out;
    }

    /**
     * Wraps a single Spring AI {@link ToolCallback} as a Copilot
     * {@link ToolDefinition}. Package-visible for testing.
     */
    ToolDefinition toCopilotTool(ToolCallback cb) {
        org.springframework.ai.tool.definition.ToolDefinition def = cb.getToolDefinition();
        String name = def.name();
        String description = def.description();
        Map<String, Object> parameters = parseSchema(def.inputSchema(), name);

        return ToolDefinition.create(name, description, parameters, invocation -> {
            // Copilot calls this back in-process when the assistant invokes the tool.
            // We serialize the arguments to JSON (Spring AI's ToolCallback contract) and
            // delegate to the callback. Return the raw string result — the SDK wraps it.
            return CompletableFuture.supplyAsync(() -> {
                try {
                    String args = argsToJson(invocation);
                    return cb.call(args);
                } catch (Exception e) {
                    log.error("Copilot tool '{}' handler failed: {}", name, e.toString(), e);
                    // Bubble up as a failed future — the SDK surfaces this as a tool
                    // error back to the assistant, which typically recovers gracefully.
                    throw new RuntimeException("Tool '" + name + "' failed: " + e.getMessage(), e);
                }
            });
        });
    }

    /**
     * Serializes a {@link ToolInvocation}'s arguments Map to a JSON string.
     * Uses the SDK's own args accessor so any type coercion the SDK performs
     * upstream is honored.
     */
    private String argsToJson(ToolInvocation invocation) {
        Map<String, Object> args = invocation.getArguments();
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        try {
            return json.writeValueAsString(args);
        } catch (Exception e) {
            log.warn("Failed to serialize tool arguments for '{}': {}",
                    invocation.getToolName(), e.toString());
            return "{}";
        }
    }

    /**
     * Parses a JSON-Schema string into a Map suitable for
     * {@code ToolDefinition.parameters()}. On parse failure returns an empty
     * map — see class javadoc.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> parseSchema(String schemaJson, String toolNameForLog) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Object parsed = json.readValue(schemaJson, Object.class);
            if (parsed instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            log.warn("Tool '{}' inputSchema is not a JSON object (was {}); dropping schema",
                    toolNameForLog, parsed == null ? "null" : parsed.getClass().getSimpleName());
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Tool '{}' inputSchema failed to parse ({}); dropping schema",
                    toolNameForLog, e.toString());
            return Collections.emptyMap();
        }
    }

    // --- Inbound: Copilot event → Spring AI ---

    /**
     * Converts a Copilot {@link AssistantMessageToolRequest} into Spring AI's
     * {@link AssistantMessage.ToolCall}. Preserves {@code toolCallId} as the
     * ToolCall's {@code id}, the tool name, and serializes the arguments
     * object (which may be a Map, String, or JsonNode depending on how the
     * SDK deserialized it) to a JSON string as Spring AI expects.
     */
    public AssistantMessage.ToolCall toSpringAiToolCall(AssistantMessageToolRequest request) {
        if (request == null) {
            return null;
        }
        String argsJson;
        Object args = request.arguments();
        if (args == null) {
            argsJson = "{}";
        } else if (args instanceof String s) {
            argsJson = s;
        } else {
            try {
                argsJson = json.writeValueAsString(args);
            } catch (Exception e) {
                log.warn("Failed to serialize inbound tool-call args for '{}': {}",
                        request.name(), e.toString());
                argsJson = "{}";
            }
        }
        // Spring AI's ToolCall record: (id, type, name, arguments)
        return new AssistantMessage.ToolCall(request.toolCallId(), "function",
                request.name(), argsJson);
    }

    /**
     * Convenience: bulk-convert all tool requests on an assistant-message
     * event data payload. Handles a null list.
     */
    public List<AssistantMessage.ToolCall> toSpringAiToolCalls(
            List<AssistantMessageToolRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<AssistantMessage.ToolCall> out = new ArrayList<>(requests.size());
        for (AssistantMessageToolRequest r : requests) {
            AssistantMessage.ToolCall tc = toSpringAiToolCall(r);
            if (tc != null) {
                out.add(tc);
            }
        }
        return out;
    }

    /**
     * Wraps a Spring AI {@link org.springframework.ai.tool.definition.ToolDefinition}
     * back-round-trip into a plain {@code DefaultToolDefinition}. Used by tests
     * verifying schema fidelity across the mapper's outbound path. Package-visible.
     */
    static org.springframework.ai.tool.definition.ToolDefinition toSpringAiDefinition(
            String name, String description, Map<String, Object> parameters) {
        String schemaJson;
        try {
            schemaJson = new tools.jackson.databind.ObjectMapper()
                    .writeValueAsString(parameters == null ? new LinkedHashMap<>() : parameters);
        } catch (Exception e) {
            schemaJson = "{}";
        }
        return DefaultToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(schemaJson)
                .build();
    }
}
