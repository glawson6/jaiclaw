package io.jaiclaw.websearch;

import java.util.List;

/**
 * SPI for pluggable web search providers.
 */
public interface WebSearchProvider {

    String id();

    List<WebSearchResult> search(String query, int maxResults) throws Exception;

    boolean isConfigured();
}
