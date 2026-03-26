package io.jaiclaw.cli.architect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses OpenAPI 3.x and Swagger 2.x specs (JSON) via Jackson tree model.
 * Extracts base URL, endpoints, and auth schemes.
 */
public final class OpenApiParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenApiParser() {}

    /**
     * Parse an OpenAPI/Swagger JSON string into structured data.
     */
    public static ParseResult parse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            boolean isV3 = root.has("openapi");
            String baseUrl = isV3 ? parseBaseUrlV3(root) : parseBaseUrlV2(root);
            List<EndpointSpec> endpoints = parsePaths(root);
            AuthConfig auth = isV3 ? parseAuthV3(root) : parseAuthV2(root);
            String title = root.path("info").path("title").asText(null);
            return new ParseResult(title, baseUrl, endpoints, auth);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OpenAPI spec: " + e.getMessage(), e);
        }
    }

    public record ParseResult(
            String title,
            String baseUrl,
            List<EndpointSpec> endpoints,
            AuthConfig auth
    ) {}

    // --- V3 parsing ---

    private static String parseBaseUrlV3(JsonNode root) {
        JsonNode servers = root.path("servers");
        if (servers.isArray() && !servers.isEmpty()) {
            return servers.get(0).path("url").asText("");
        }
        return "";
    }

    private static AuthConfig parseAuthV3(JsonNode root) {
        JsonNode schemes = root.path("components").path("securitySchemes");
        if (schemes.isMissingNode() || !schemes.isObject()) return AuthConfig.NONE;

        Iterator<Map.Entry<String, JsonNode>> fields = schemes.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode scheme = entry.getValue();
            String type = scheme.path("type").asText("");

            if ("apiKey".equals(type)) {
                return new AuthConfig(
                        "header",
                        scheme.path("name").asText("X-API-Key"),
                        "",
                        null, null, null, null, null, null, List.of());
            }
            if ("http".equals(type)) {
                String httpScheme = scheme.path("scheme").asText("");
                if ("bearer".equals(httpScheme)) {
                    return new AuthConfig(
                            "header", "Authorization", "Bearer ",
                            null, null, null, null, null, null, List.of());
                }
                if ("basic".equals(httpScheme)) {
                    return new AuthConfig(
                            "basic", null, null, null,
                            null, null, null, null, null, List.of());
                }
            }
            if ("oauth2".equals(type)) {
                JsonNode flows = scheme.path("flows").path("clientCredentials");
                String tokenUrl = flows.path("tokenUrl").asText("");
                return new AuthConfig(
                        "oauth2", null, null, null, null, null,
                        tokenUrl, null, null, List.of());
            }
        }
        return AuthConfig.NONE;
    }

    // --- V2 parsing ---

    private static String parseBaseUrlV2(JsonNode root) {
        String host = root.path("host").asText("");
        String basePath = root.path("basePath").asText("");
        String scheme = "https";
        JsonNode schemes = root.path("schemes");
        if (schemes.isArray() && !schemes.isEmpty()) {
            scheme = schemes.get(0).asText("https");
        }
        if (host.isEmpty()) return "";
        return scheme + "://" + host + basePath;
    }

    private static AuthConfig parseAuthV2(JsonNode root) {
        JsonNode defs = root.path("securityDefinitions");
        if (defs.isMissingNode() || !defs.isObject()) return AuthConfig.NONE;

        Iterator<Map.Entry<String, JsonNode>> fields = defs.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode def = entry.getValue();
            String type = def.path("type").asText("");

            if ("apiKey".equals(type)) {
                return new AuthConfig(
                        "header",
                        def.path("name").asText("X-API-Key"),
                        "",
                        null, null, null, null, null, null, List.of());
            }
            if ("basic".equals(type)) {
                return new AuthConfig(
                        "basic", null, null, null,
                        null, null, null, null, null, List.of());
            }
            if ("oauth2".equals(type)) {
                String tokenUrl = def.path("tokenUrl").asText("");
                return new AuthConfig(
                        "oauth2", null, null, null, null, null,
                        tokenUrl, null, null, List.of());
            }
        }
        return AuthConfig.NONE;
    }

    // --- Shared path parsing ---

    private static List<EndpointSpec> parsePaths(JsonNode root) {
        var endpoints = new ArrayList<EndpointSpec>();
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) return endpoints;

        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            var pathEntry = pathEntries.next();
            String path = pathEntry.getKey();
            JsonNode methods = pathEntry.getValue();

            for (String method : List.of("get", "post", "put", "delete", "patch")) {
                JsonNode operation = methods.path(method);
                if (operation.isMissingNode()) continue;

                String operationId = operation.path("operationId").asText(null);
                String summary = operation.path("summary").asText(operation.path("description").asText(null));
                String tag = null;
                JsonNode tags = operation.path("tags");
                if (tags.isArray() && !tags.isEmpty()) {
                    tag = tags.get(0).asText(null);
                }

                List<EndpointSpec.ParamSpec> params = parseParams(operation, methods);

                endpoints.add(new EndpointSpec(
                        method.toUpperCase(), path, operationId, summary, null, tag, params));
            }
        }
        return endpoints;
    }

    private static List<EndpointSpec.ParamSpec> parseParams(JsonNode operation, JsonNode pathItem) {
        var params = new ArrayList<EndpointSpec.ParamSpec>();

        // Path-level parameters
        addParams(pathItem.path("parameters"), params);
        // Operation-level parameters (override path-level)
        addParams(operation.path("parameters"), params);

        return params;
    }

    private static void addParams(JsonNode paramsNode, List<EndpointSpec.ParamSpec> params) {
        if (!paramsNode.isArray()) return;
        for (JsonNode p : paramsNode) {
            String name = p.path("name").asText("");
            String in = p.path("in").asText("query");
            boolean required = p.path("required").asBoolean(false);
            String type = p.path("schema").path("type").asText(
                    p.path("type").asText("string"));
            if (!name.isEmpty()) {
                params.add(new EndpointSpec.ParamSpec(name, type, in, required));
            }
        }
    }
}
