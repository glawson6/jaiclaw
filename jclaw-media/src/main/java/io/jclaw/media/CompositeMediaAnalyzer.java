package io.jclaw.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Delegates media analysis to the first provider that supports the given MIME type.
 */
public class CompositeMediaAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CompositeMediaAnalyzer.class);

    private final List<MediaAnalysisProvider> providers;

    public CompositeMediaAnalyzer(List<MediaAnalysisProvider> providers) {
        this.providers = providers != null ? List.copyOf(providers) : List.of();
    }

    public CompletableFuture<MediaAnalysisResult> analyze(MediaInput input) {
        return findProvider(input.mimeType())
                .map(provider -> {
                    log.debug("Analyzing {} ({}) with provider: {}",
                            input.filename(), input.mimeType(), provider.name());
                    return provider.analyze(input);
                })
                .orElseGet(() -> {
                    log.warn("No media analysis provider found for MIME type: {}", input.mimeType());
                    return CompletableFuture.completedFuture(MediaAnalysisResult.empty());
                });
    }

    public Optional<MediaAnalysisProvider> findProvider(String mimeType) {
        return providers.stream()
                .filter(p -> p.supports(mimeType))
                .findFirst();
    }

    public int providerCount() {
        return providers.size();
    }
}
