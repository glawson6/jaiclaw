package io.jaiclaw.memory;

public record MemorySearchResult(
        String path,
        int startLine,
        int endLine,
        double score,
        String snippet,
        MemorySource source
) {
}
