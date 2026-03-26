package io.jaiclaw.docstore.analysis;

import io.jaiclaw.docstore.model.AnalysisResult;
import io.jaiclaw.documents.CompositeDocumentParser;
import io.jaiclaw.documents.DocumentIngestionPipeline;
import io.jaiclaw.documents.DocumentIngestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * LLM-powered document analyzer. Extracts text via {@link DocumentIngestionPipeline},
 * then sends it to the LLM for summarization, topic detection, and entity extraction.
 *
 * <p>The {@code llmCall} function is injected at construction, decoupling this class
 * from any specific AI framework.
 */
public class LlmDocStoreAnalyzer implements DocStoreAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LlmDocStoreAnalyzer.class);

    private static final String ANALYSIS_PROMPT = """
            Analyze the following document and provide:
            1. SUMMARY: A 2-3 sentence summary
            2. TOPICS: Comma-separated list of 3-5 topics/themes
            3. ENTITIES: Comma-separated list of named entities (people, organizations, dates, amounts)

            Format your response exactly as:
            SUMMARY: <summary>
            TOPICS: <topic1>, <topic2>, ...
            ENTITIES: <entity1>, <entity2>, ...

            Document (%s):
            %s
            """;

    private static final int MAX_TEXT_LENGTH = 15000;

    private final DocumentIngestionPipeline pipeline;
    private final Function<String, String> llmCall;

    public LlmDocStoreAnalyzer(Function<String, String> llmCall) {
        this.pipeline = new DocumentIngestionPipeline(CompositeDocumentParser.withDefaults());
        this.llmCall = llmCall;
    }

    public LlmDocStoreAnalyzer(DocumentIngestionPipeline pipeline, Function<String, String> llmCall) {
        this.pipeline = pipeline;
        this.llmCall = llmCall;
    }

    @Override
    public AnalysisResult analyze(byte[] content, String mimeType, String filename) {
        try {
            DocumentIngestionResult result = pipeline.ingest(content, mimeType);
            String text = result.text();

            String truncated = text.length() > MAX_TEXT_LENGTH
                    ? text.substring(0, MAX_TEXT_LENGTH) + "\n... [truncated]"
                    : text;

            String prompt = ANALYSIS_PROMPT.formatted(filename != null ? filename : "document", truncated);
            String llmResponse = llmCall.apply(prompt);

            return parseResponse(llmResponse, text, result.metadata());
        } catch (Exception e) {
            log.warn("LLM analysis failed for {}: {}", filename, e.getMessage());
            return new AnalysisResult(
                    "LLM analysis failed: " + e.getMessage(),
                    "",
                    List.of(),
                    List.of(),
                    Map.of(),
                    AnalysisResult.AnalysisLevel.LLM
            );
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return pipeline.supports(mimeType);
    }

    private AnalysisResult parseResponse(String response, String extractedText, Map<String, String> metadata) {
        String summary = extractField(response, "SUMMARY:");
        List<String> topics = extractList(response, "TOPICS:");
        List<String> entities = extractList(response, "ENTITIES:");

        return new AnalysisResult(
                summary.isEmpty() ? response.strip() : summary,
                extractedText,
                topics,
                entities,
                metadata,
                AnalysisResult.AnalysisLevel.LLM
        );
    }

    private String extractField(String response, String prefix) {
        for (String line : response.split("\n")) {
            if (line.strip().toUpperCase().startsWith(prefix.toUpperCase())) {
                return line.strip().substring(prefix.length()).strip();
            }
        }
        return "";
    }

    private List<String> extractList(String response, String prefix) {
        String field = extractField(response, prefix);
        if (field.isEmpty()) return List.of();
        return Arrays.stream(field.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
