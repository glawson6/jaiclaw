///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS io.jaiclaw:jaiclaw-ascii-render:0.8.1-SNAPSHOT
//DEPS tools.jackson.core:jackson-databind:2.18.2
//REPOS taptech=https://tooling.taptech.net/repository/maven-public/

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import io.jaiclaw.asciirender.factory.AsciiBox;
import io.jaiclaw.asciirender.factory.AsciiSceneFactory;
import io.jaiclaw.asciirender.factory.SceneSpec;
import io.jaiclaw.asciirender.factory.SceneSpecException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP stdio server exposing the JaiClaw ASCII renderer as two tools:
 * {@code ascii_box} and {@code ascii_render}.
 *
 * <p>Speaks JSON-RPC 2.0 over stdin/stdout, one message per line.
 * Implements only the handshake methods Claude Desktop needs:
 * {@code initialize}, {@code tools/list}, {@code tools/call}.
 *
 * <p>Diagnostics are written to stderr so stdout stays a clean
 * JSON-RPC stream.
 */
public class AsciiRenderMcpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "jaiclaw-ascii-render";
    private static final String SERVER_VERSION = "0.8.1";

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = System.err;

        err.println("[ascii-render-mcp] ready");

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JsonNode req = MAPPER.readTree(line);
                JsonNode response = handle(req);
                if (response != null) {
                    out.println(MAPPER.writeValueAsString(response));
                }
            } catch (Exception e) {
                err.println("[ascii-render-mcp] error: " + e.getMessage());
                e.printStackTrace(err);
            }
        }
    }

    private static JsonNode handle(JsonNode req) {
        JsonNode idNode = req.get("id");
        String method = req.path("method").asText("");

        // Notifications (no id) get no response.
        boolean isNotification = idNode == null || idNode.isNull();

        switch (method) {
            case "initialize":
                return initializeResult(idNode);
            case "notifications/initialized":
                return null;
            case "tools/list":
                return toolsListResult(idNode);
            case "tools/call":
                return toolsCallResult(idNode, req.path("params"));
            case "ping":
                return emptyResult(idNode);
            default:
                if (isNotification) return null;
                return errorResponse(idNode, -32601, "Method not found: " + method);
        }
    }

    private static JsonNode initializeResult(JsonNode id) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode caps = result.putObject("capabilities");
        caps.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        return wrapResult(id, result);
    }

    private static JsonNode toolsListResult(JsonNode id) {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(asciiBoxToolSchema());
        tools.add(asciiRenderToolSchema());
        return wrapResult(id, result);
    }

    private static ObjectNode asciiBoxToolSchema() {
        ObjectNode tool = MAPPER.createObjectNode();
        tool.put("name", "ascii_box");
        tool.put("description",
                "Wrap text in a Unicode-bordered ASCII box. Use for status messages, "
                + "callouts, banners. Supports four border styles (single, double, "
                + "bold, rounded) and an optional title on the top edge.");
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        ObjectNode content = props.putObject("content");
        content.put("type", "string");
        content.put("description", "Text inside the box. Embedded newlines are honoured; long lines wrap at word boundaries.");

        ObjectNode width = props.putObject("width");
        width.put("type", "integer");
        width.put("description", "Maximum inner content width in characters (default 60).");

        ObjectNode border = props.putObject("border");
        border.put("type", "string");
        border.put("description", "Border style: single | double | bold | rounded (default single).");

        ObjectNode title = props.putObject("title");
        title.put("type", "string");
        title.put("description", "Optional title rendered on the top edge in [brackets].");

        ArrayNode required = schema.putArray("required");
        required.add("content");
        return tool;
    }

    private static ObjectNode asciiRenderToolSchema() {
        ObjectNode tool = MAPPER.createObjectNode();
        tool.put("name", "ascii_render");
        tool.put("description",
                "Render a structured ASCII scene from a canvas spec. Use for diagrams "
                + "with multiple boxes, lines, labels, plots, tables. Supports element "
                + "types: rectangle, line, label, text, dot, circle, ellipse, table, plot. "
                + "Coordinates: origin (0,0) at top-left; x grows right, y grows down.");
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        ObjectNode w = props.putObject("width");
        w.put("type", "integer");
        w.put("description", "Canvas width in characters.");

        ObjectNode h = props.putObject("height");
        h.put("type", "integer");
        h.put("description", "Canvas height in characters.");

        ObjectNode trim = props.putObject("trim");
        trim.put("type", "boolean");
        trim.put("description", "Trim trailing whitespace from each rendered line (default true).");

        ObjectNode elements = props.putObject("elements");
        elements.put("type", "array");
        elements.put("description", "List of {type, params} entries. type: rectangle | line | label | text | dot | circle | ellipse | table | plot. params: object with x/y/width/height/text/etc as appropriate.");

        ArrayNode required = schema.putArray("required");
        required.add("width");
        required.add("height");
        required.add("elements");
        return tool;
    }

    private static JsonNode toolsCallResult(JsonNode id, JsonNode params) {
        String toolName = params.path("name").asText("");
        JsonNode argsNode = params.path("arguments");
        try {
            String rendered = switch (toolName) {
                case "ascii_box" -> callAsciiBox(argsNode);
                case "ascii_render" -> callAsciiRender(argsNode);
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };
            return wrapToolResult(id, rendered, false);
        } catch (Exception e) {
            return wrapToolResult(id, "Error: " + e.getMessage(), true);
        }
    }

    private static String callAsciiBox(JsonNode args) {
        String content = args.path("content").asText("");
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: content");
        }
        int width = args.has("width") && !args.get("width").isNull()
                ? args.get("width").asInt(AsciiBox.DEFAULT_WIDTH)
                : AsciiBox.DEFAULT_WIDTH;
        AsciiBox.Style style = parseStyle(args.path("border").asText("single"));
        String title = args.has("title") && !args.get("title").isNull()
                ? args.get("title").asText()
                : null;
        return AsciiBox.render(content, width, style, title);
    }

    private static String callAsciiRender(JsonNode args) {
        Map<String, Object> paramMap = MAPPER.convertValue(args, LinkedHashMap.class);
        try {
            SceneSpec scene = AsciiSceneFactory.fromMap(paramMap);
            return AsciiSceneFactory.render(scene);
        } catch (SceneSpecException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static AsciiBox.Style parseStyle(String raw) {
        if (raw == null || raw.isBlank()) return AsciiBox.Style.SINGLE;
        return switch (raw.trim().toLowerCase()) {
            case "double" -> AsciiBox.Style.DOUBLE;
            case "bold", "heavy" -> AsciiBox.Style.BOLD;
            case "rounded", "round" -> AsciiBox.Style.ROUNDED;
            default -> AsciiBox.Style.SINGLE;
        };
    }

    private static ObjectNode wrapResult(JsonNode id, JsonNode result) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? null : id);
        response.set("result", result);
        return response;
    }

    private static ObjectNode wrapToolResult(JsonNode id, String text, boolean isError) {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode block = content.addObject();
        block.put("type", "text");
        block.put("text", text);
        result.put("isError", isError);
        return wrapResult(id, result);
    }

    private static ObjectNode emptyResult(JsonNode id) {
        return wrapResult(id, MAPPER.createObjectNode());
    }

    private static ObjectNode errorResponse(JsonNode id, int code, String message) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }
}
