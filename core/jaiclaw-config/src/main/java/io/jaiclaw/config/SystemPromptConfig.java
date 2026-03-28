package io.jaiclaw.config;

/**
 * Per-tenant system prompt resolution configuration.
 *
 * @param strategy "inline" (default), "classpath", "file", or "url"
 * @param content  inline prompt text or fallback content
 * @param source   resource path or URL for non-inline strategies
 * @param append   true = append loaded content to auto-generated prompt; false = replace entirely
 */
public record SystemPromptConfig(
        String strategy,
        String content,
        String source,
        boolean append
) {
    public static final SystemPromptConfig DEFAULT = new SystemPromptConfig(
            "inline", null, null, false
    );

    public SystemPromptConfig {
        if (strategy == null || strategy.isBlank()) strategy = "inline";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String strategy;
        private String content;
        private String source;
        private boolean append;

        public Builder strategy(String strategy) { this.strategy = strategy; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder append(boolean append) { this.append = append; return this; }

        public SystemPromptConfig build() {
            return new SystemPromptConfig(strategy, content, source, append);
        }
    }
}
