package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import io.jaiclaw.core.http.ProxyAwareHttpClientFactory;
import io.jaiclaw.tools.exec.SsrfGuard;
import net.dankito.readability4j.Readability4J;
import net.dankito.readability4j.Article;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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
                },
                "extractReadable": {
                  "type": "boolean",
                  "description": "Extract clean readable text from HTML using Readability (default true)"
                }
              },
              "required": ["url"]
            }""";

    private final HttpClient httpClient;
    private final boolean ssrfProtection;

    public WebFetchTool() {
        this(true);
    }

    public WebFetchTool(boolean ssrfProtection) {
        this(ProxyAwareHttpClientFactory.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build(), ssrfProtection);
    }

    public WebFetchTool(HttpClient httpClient) {
        this(httpClient, true);
    }

    public WebFetchTool(HttpClient httpClient, boolean ssrfProtection) {
        super(new ToolDefinition(
                "web_fetch",
                "Fetch content from a URL. Returns the HTTP status code and response body.",
                ToolCatalog.SECTION_WEB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
        this.httpClient = httpClient;
        this.ssrfProtection = ssrfProtection;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String url = requireParam(parameters, "url");
        int timeout = parameters.containsKey("timeout")
                ? ((Number) parameters.get("timeout")).intValue() : 30;
        boolean extractReadable = parameters.containsKey("extractReadable")
                ? Boolean.TRUE.equals(parameters.get("extractReadable")) : true;

        // SSRF protection: block requests to private/internal addresses
        if (ssrfProtection) {
            Optional<String> ssrfError = SsrfGuard.validate(url);
            if (ssrfError.isPresent()) {
                return new ToolResult.Error("SSRF protection: " + ssrfError.get());
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .header("User-Agent", "JaiClaw/0.1")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        String body = response.body();

        if (status >= 400) {
            return new ToolResult.Error("HTTP " + status + ":\n" + truncate(body, 2000));
        }

        // Extract readable content from HTML if requested
        if (extractReadable && isHtml(body)) {
            body = extractReadableContent(url, body);
        }

        return new ToolResult.Success(truncate(body, 50_000), Map.of("statusCode", status));
    }

    private static String extractReadableContent(String url, String html) {
        try {
            Article article = new Readability4J(url, html).parse();
            String readable = article.getTextContent();
            if (readable != null && !readable.isBlank()) {
                String title = article.getTitle();
                String prefix = title != null && !title.isBlank() ? "# " + title + "\n\n" : "";
                return prefix + readable.strip();
            }
        } catch (Exception ignored) {
            // Fall back to raw HTML on extraction failure
        }
        return html;
    }

    private static boolean isHtml(String body) {
        if (body == null || body.length() < 15) return false;
        String trimmed = body.stripLeading().toLowerCase();
        return trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html");
    }

    private static String truncate(String s, int maxLength) {
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "\n... (truncated)";
    }
}
