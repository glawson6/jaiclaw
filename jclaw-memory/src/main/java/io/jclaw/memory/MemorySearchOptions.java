package io.jclaw.memory;

public record MemorySearchOptions(
        int maxResults,
        double minScore,
        String sessionKey
) {
    public static final MemorySearchOptions DEFAULT = new MemorySearchOptions(10, 0.5, null);
}
