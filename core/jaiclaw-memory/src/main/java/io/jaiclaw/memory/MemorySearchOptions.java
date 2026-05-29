package io.jaiclaw.memory;

public record MemorySearchOptions(
        int maxResults,
        double minScore,
        String sessionKey,
        SearchMode searchMode,
        QueryScope queryScope
) {
    public static final MemorySearchOptions DEFAULT = new MemorySearchOptions(10, 0.5, null, SearchMode.DEFAULT, QueryScope.GLOBAL);

    /** Backward-compatible 3-arg constructor. */
    public MemorySearchOptions(int maxResults, double minScore, String sessionKey) {
        this(maxResults, minScore, sessionKey, SearchMode.DEFAULT, QueryScope.GLOBAL);
    }
}
