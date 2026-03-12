package io.jclaw.media;

import java.util.concurrent.CompletableFuture;

/**
 * SPI for async media analysis. Implementations use vision LLMs,
 * audio transcription services, or other analysis backends.
 */
public interface MediaAnalysisProvider {

    /**
     * Whether this provider can analyze the given media type.
     */
    boolean supports(String mimeType);

    /**
     * Analyze media content asynchronously.
     */
    CompletableFuture<MediaAnalysisResult> analyze(MediaInput input);

    /**
     * Provider name for logging/metrics.
     */
    String name();
}
