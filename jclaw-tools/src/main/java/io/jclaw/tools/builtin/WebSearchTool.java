package io.jclaw.tools.builtin;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Performs a web search using a configurable search API.
 * Defaults to a DuckDuckGo HTML search as a zero-config fallback.
 * Production usage should configure a proper search API key.
 */
public class WebSearchTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "The search query"
                },
                "maxResults": {
                  "type": "integer",
                  "description": "Maximum number of results to return (default 5)"
                }
              },
              "required": ["query"]
            }""";

    private final HttpClient httpClient;

    public WebSearchTool() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public WebSearchTool(HttpClient httpClient) {
        super(new ToolDefinition(
                "web_search",
                "Search the web for information. Returns search results with titles and URLs.",
                ToolCatalog.SECTION_WEB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
        this.httpClient = httpClient;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String query = requireParam(parameters, "query");
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

        // DuckDuckGo HTML lite as zero-config fallback
        String url = "https://html.duckduckgo.com/html/?q=" + encoded;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "JClaw/0.1")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            return new ToolResult.Error("Search failed: HTTP " + response.statusCode());
        }

        // Extract basic results from DuckDuckGo HTML
        String body = response.body();
        String results = extractSearchResults(body);

        return new ToolResult.Success(results.isEmpty()
                ? "No results found for: " + query
                : results);
    }

    private String extractSearchResults(String html) {
        var sb = new StringBuilder();
        int count = 0;

        // Simple extraction of result links from DuckDuckGo HTML lite
        int idx = 0;
        while ((idx = html.indexOf("class=\"result__a\"", idx)) != -1 && count < 10) {
            int hrefStart = html.lastIndexOf("href=\"", idx);
            if (hrefStart == -1) { idx++; continue; }
            hrefStart += 6;
            int hrefEnd = html.indexOf("\"", hrefStart);
            if (hrefEnd == -1) { idx++; continue; }
            String href = html.substring(hrefStart, hrefEnd);

            // Get title text
            int tagEnd = html.indexOf(">", idx);
            int closeTag = html.indexOf("</a>", tagEnd);
            String title = (tagEnd != -1 && closeTag != -1)
                    ? html.substring(tagEnd + 1, closeTag).replaceAll("<[^>]+>", "").trim()
                    : "";

            // Get snippet
            int snippetStart = html.indexOf("class=\"result__snippet\"", closeTag != -1 ? closeTag : idx);
            String snippet = "";
            if (snippetStart != -1) {
                int sTagEnd = html.indexOf(">", snippetStart);
                int sCloseTag = html.indexOf("</", sTagEnd);
                if (sTagEnd != -1 && sCloseTag != -1) {
                    snippet = html.substring(sTagEnd + 1, sCloseTag).replaceAll("<[^>]+>", "").trim();
                }
            }

            count++;
            sb.append(count).append(". ").append(title).append('\n');
            sb.append("   URL: ").append(href).append('\n');
            if (!snippet.isEmpty()) {
                sb.append("   ").append(snippet).append('\n');
            }
            sb.append('\n');

            idx = closeTag != -1 ? closeTag : idx + 1;
        }
        return sb.toString();
    }
}
