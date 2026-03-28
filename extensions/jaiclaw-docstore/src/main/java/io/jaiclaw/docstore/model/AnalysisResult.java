package io.jaiclaw.docstore.model;

import java.util.List;
import java.util.Map;

/**
 * Result of analyzing a document — from basic text extraction or LLM-powered analysis.
 *
 * @param summary       short summary of the document
 * @param extractedText full extracted text (for search indexing)
 * @param topics        detected topics or themes
 * @param entities      named entities (people, organizations, dates)
 * @param metadata      parser-extracted metadata (page count, author, etc.)
 * @param level         analysis level that produced this result
 */
public record AnalysisResult(
        String summary,
        String extractedText,
        List<String> topics,
        List<String> entities,
        Map<String, String> metadata,
        AnalysisLevel level
) {
    public enum AnalysisLevel { BASIC, LLM }

    public AnalysisResult {
        if (topics == null) topics = List.of();
        if (entities == null) entities = List.of();
        if (metadata == null) metadata = Map.of();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String summary;
        private String extractedText;
        private List<String> topics;
        private List<String> entities;
        private Map<String, String> metadata;
        private AnalysisLevel level;

        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder extractedText(String extractedText) { this.extractedText = extractedText; return this; }
        public Builder topics(List<String> topics) { this.topics = topics; return this; }
        public Builder entities(List<String> entities) { this.entities = entities; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder level(AnalysisLevel level) { this.level = level; return this; }

        public AnalysisResult build() {
            return new AnalysisResult(summary, extractedText, topics, entities, metadata, level);
        }
    }
}
