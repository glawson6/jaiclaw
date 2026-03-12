package io.jclaw.documents;

import java.util.List;
import java.util.Map;

/**
 * Result of the document ingestion pipeline — parsed text, chunks, and metadata.
 *
 * @param text     full extracted text
 * @param chunks   text split into chunks for embedding
 * @param metadata document metadata from parsing
 */
public record DocumentIngestionResult(
        String text,
        List<String> chunks,
        Map<String, String> metadata
) {
    public DocumentIngestionResult {
        if (text == null) text = "";
        if (chunks == null) chunks = List.of();
        if (metadata == null) metadata = Map.of();
    }

    public int chunkCount() {
        return chunks.size();
    }
}
