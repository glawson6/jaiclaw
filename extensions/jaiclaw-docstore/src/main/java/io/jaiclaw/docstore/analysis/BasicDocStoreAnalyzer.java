package io.jaiclaw.docstore.analysis;

import io.jaiclaw.docstore.model.AnalysisResult;
import io.jaiclaw.documents.CompositeDocumentParser;
import io.jaiclaw.documents.DocumentIngestionPipeline;
import io.jaiclaw.documents.DocumentIngestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Basic document analyzer using {@link DocumentIngestionPipeline} for text extraction.
 * No LLM calls — extracts text, metadata, and generates a simple summary from the first paragraph.
 */
public class BasicDocStoreAnalyzer implements DocStoreAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(BasicDocStoreAnalyzer.class);

    private final DocumentIngestionPipeline pipeline;

    public BasicDocStoreAnalyzer() {
        this.pipeline = new DocumentIngestionPipeline(CompositeDocumentParser.withDefaults());
    }

    public BasicDocStoreAnalyzer(DocumentIngestionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public AnalysisResult analyze(byte[] content, String mimeType, String filename) {
        try {
            DocumentIngestionResult result = pipeline.ingest(content, mimeType);
            String text = result.text();
            String summary = extractFirstParagraph(text);

            return new AnalysisResult(
                    summary,
                    text,
                    List.of(),
                    List.of(),
                    result.metadata(),
                    AnalysisResult.AnalysisLevel.BASIC
            );
        } catch (Exception e) {
            log.warn("Basic analysis failed for {}: {}", filename, e.getMessage());
            return new AnalysisResult(
                    "Analysis failed: " + e.getMessage(),
                    "",
                    List.of(),
                    List.of(),
                    Map.of(),
                    AnalysisResult.AnalysisLevel.BASIC
            );
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return pipeline.supports(mimeType);
    }

    private String extractFirstParagraph(String text) {
        if (text == null || text.isBlank()) return "";
        String trimmed = text.strip();
        int end = trimmed.indexOf("\n\n");
        if (end < 0) end = Math.min(trimmed.length(), 300);
        String paragraph = trimmed.substring(0, end).strip();
        return paragraph.length() > 300 ? paragraph.substring(0, 300) + "..." : paragraph;
    }
}
