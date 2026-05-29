package io.jaiclaw.websearch;

/**
 * A single web search result.
 */
public record WebSearchResult(String title, String url, String snippet) {
}
