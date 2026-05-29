package io.jaiclaw.websearch.provider;

import io.jaiclaw.core.http.ProxyAwareHttpClientFactory;
import io.jaiclaw.websearch.WebSearchProvider;
import io.jaiclaw.websearch.WebSearchResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DuckDuckGo HTML lite search — zero-config, always available.
 */
public class DuckDuckGoSearchProvider implements WebSearchProvider {

    private final HttpClient httpClient;

    public DuckDuckGoSearchProvider() {
        this(ProxyAwareHttpClientFactory.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public DuckDuckGoSearchProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String id() {
        return "duckduckgo";
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://html.duckduckgo.com/html/?q=" + encoded;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "JaiClaw/0.1")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("DuckDuckGo search failed: HTTP " + response.statusCode());
        }

        return extractResults(response.body(), maxResults);
    }

    @Override
    public boolean isConfigured() {
        return true; // Always available — no API key needed
    }

    private List<WebSearchResult> extractResults(String html, int maxResults) {
        List<WebSearchResult> results = new ArrayList<>();
        int idx = 0;

        while ((idx = html.indexOf("class=\"result__a\"", idx)) != -1 && results.size() < maxResults) {
            int hrefStart = html.lastIndexOf("href=\"", idx);
            if (hrefStart == -1) { idx++; continue; }
            hrefStart += 6;
            int hrefEnd = html.indexOf("\"", hrefStart);
            if (hrefEnd == -1) { idx++; continue; }
            String href = html.substring(hrefStart, hrefEnd);

            int tagEnd = html.indexOf(">", idx);
            int closeTag = html.indexOf("</a>", tagEnd);
            String title = (tagEnd != -1 && closeTag != -1)
                    ? html.substring(tagEnd + 1, closeTag).replaceAll("<[^>]+>", "").trim()
                    : "";

            int snippetStart = html.indexOf("class=\"result__snippet\"", closeTag != -1 ? closeTag : idx);
            String snippet = "";
            if (snippetStart != -1) {
                int sTagEnd = html.indexOf(">", snippetStart);
                int sCloseTag = html.indexOf("</", sTagEnd);
                if (sTagEnd != -1 && sCloseTag != -1) {
                    snippet = html.substring(sTagEnd + 1, sCloseTag).replaceAll("<[^>]+>", "").trim();
                }
            }

            results.add(new WebSearchResult(title, href, snippet));
            idx = closeTag != -1 ? closeTag : idx + 1;
        }

        return results;
    }
}
