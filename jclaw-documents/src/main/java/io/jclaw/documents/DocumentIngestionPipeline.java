package io.jclaw.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end pipeline: parse document bytes → extract text → chunk text.
 * This is the main entry point for document ingestion in JClaw.
 */
public class DocumentIngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionPipeline.class);

    private final DocumentParser parser;
    private final TextChunker chunker;
    private final ChunkingStrategy defaultStrategy;

    public DocumentIngestionPipeline(DocumentParser parser, TextChunker chunker, ChunkingStrategy defaultStrategy) {
        this.parser = parser;
        this.chunker = chunker;
        this.defaultStrategy = defaultStrategy;
    }

    public DocumentIngestionPipeline(DocumentParser parser) {
        this(parser, new TextChunker(), ChunkingStrategy.defaults());
    }

    /**
     * Parse and chunk a document using the default chunking strategy.
     */
    public DocumentIngestionResult ingest(byte[] bytes, String mimeType) {
        return ingest(bytes, mimeType, defaultStrategy);
    }

    /**
     * Parse and chunk a document using a custom chunking strategy.
     */
    public DocumentIngestionResult ingest(byte[] bytes, String mimeType, ChunkingStrategy strategy) {
        if (!parser.supports(mimeType)) {
            throw new DocumentParseException("Unsupported document type: " + mimeType);
        }

        log.debug("Ingesting document: mimeType={}, size={} bytes", mimeType, bytes.length);

        ParsedDocument parsed = parser.parse(bytes, mimeType);
        var chunks = chunker.chunk(parsed.text(), strategy);

        log.debug("Ingestion complete: {} chars, {} chunks", parsed.text().length(), chunks.size());

        return new DocumentIngestionResult(parsed.text(), chunks, parsed.metadata());
    }

    /**
     * Whether this pipeline can handle the given MIME type.
     */
    public boolean supports(String mimeType) {
        return parser.supports(mimeType);
    }
}
