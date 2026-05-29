package io.jaiclaw.websearch;

/**
 * Configuration properties for web search.
 */
public record WebSearchProperties(
        String provider,
        String braveApiKey,
        String tavilyApiKey,
        int defaultMaxResults
) {
    public static final WebSearchProperties DEFAULT =
            new WebSearchProperties("duckduckgo", null, null, 5);
}
