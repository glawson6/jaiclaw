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
}
