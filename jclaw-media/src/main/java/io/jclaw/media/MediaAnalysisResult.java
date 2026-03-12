package io.jclaw.media;

import java.util.List;
import java.util.Map;

/**
 * Result of media analysis — structured output from vision/audio LLM processing.
 *
 * @param description  human-readable description of the media content
 * @param tags         extracted tags/labels
 * @param metadata     additional structured data (transcription, objects detected, etc.)
 * @param confidence   overall confidence score (0.0 - 1.0)
 */
public record MediaAnalysisResult(
        String description,
        List<String> tags,
        Map<String, Object> metadata,
        double confidence
) {
    public MediaAnalysisResult {
        if (description == null) description = "";
        if (tags == null) tags = List.of();
        if (metadata == null) metadata = Map.of();
        if (confidence < 0.0) confidence = 0.0;
        if (confidence > 1.0) confidence = 1.0;
    }

    public static MediaAnalysisResult empty() {
        return new MediaAnalysisResult("", List.of(), Map.of(), 0.0);
    }

    public static MediaAnalysisResult of(String description, List<String> tags) {
        return new MediaAnalysisResult(description, tags, Map.of(), 1.0);
    }

    public boolean isEmpty() {
        return description.isEmpty() && tags.isEmpty();
    }
}
