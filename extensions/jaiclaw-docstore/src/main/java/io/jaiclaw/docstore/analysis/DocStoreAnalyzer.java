package io.jaiclaw.docstore.analysis;

import io.jaiclaw.docstore.model.AnalysisResult;

/**
 * SPI for analyzing document content. Implementations range from basic text extraction
 * to LLM-powered summarization and entity extraction.
 */
public interface DocStoreAnalyzer {

    AnalysisResult analyze(byte[] content, String mimeType, String filename);

    boolean supports(String mimeType);
}
