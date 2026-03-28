package io.jaiclaw.memory;

public record MemorySearchResult(
        String path,
        int startLine,
        int endLine,
        double score,
        String snippet,
        MemorySource source
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String path;
        private int startLine;
        private int endLine;
        private double score;
        private String snippet;
        private MemorySource source;

        public Builder path(String path) { this.path = path; return this; }
        public Builder startLine(int startLine) { this.startLine = startLine; return this; }
        public Builder endLine(int endLine) { this.endLine = endLine; return this; }
        public Builder score(double score) { this.score = score; return this; }
        public Builder snippet(String snippet) { this.snippet = snippet; return this; }
        public Builder source(MemorySource source) { this.source = source; return this; }

        public MemorySearchResult build() {
            return new MemorySearchResult(path, startLine, endLine, score, snippet, source);
        }
    }
}
