package io.jclaw.tools.builtin;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Fetches content from a URL and returns the response body.
 */
public class WebFetchTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "url": {
                  "type": "string",
                  "description": "The URL to fetch content from"
                },
                "timeout": {
                  "type": "integer",
                  "description": "Timeout in seconds (default 30)"
                }
              },
              "required": ["url"]
            }""";

    private final HttpClient httpClient;

    public WebFetchTool() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public WebFetchTool(HttpClient httpClient) {
        super(new ToolDefinition(
                "web_fetch",
                "Fetch content from a URL. Returns the HTTP status code and response body.",
                ToolCatalog.SECTION_WEB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
        this.httpClient = httpClient;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String url = requireParam(parameters, "url");
        int timeout = parameters.containsKey("timeout")
                ? ((Number) parameters.get("timeout")).intValue() : 30;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .header("User-Agent", "JClaw/0.1")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        String body = response.body();

        if (status >= 400) {
            return new ToolResult.Error("HTTP " + status + ":\n" + truncate(body, 2000));
        }
        return new ToolResult.Success(truncate(body, 50_000), Map.of("statusCode", status));
    }

    private static String truncate(String s, int maxLength) {
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "\n... (truncated)";
    }
}
