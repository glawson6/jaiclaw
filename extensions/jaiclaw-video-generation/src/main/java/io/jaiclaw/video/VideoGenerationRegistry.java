package io.jaiclaw.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of video generation providers. Resolves providers by ID
 * and delegates submit/poll operations.
 */
public class VideoGenerationRegistry {

    private static final Logger log = LoggerFactory.getLogger(VideoGenerationRegistry.class);

    private final Map<String, VideoGenerationProvider> providers = new ConcurrentHashMap<>();

    public VideoGenerationRegistry(List<VideoGenerationProvider> providerList) {
        for (VideoGenerationProvider provider : providerList) {
            providers.put(provider.providerId(), provider);
            log.info("Registered video generation provider: {}", provider.providerId());
        }
    }

    public Optional<VideoGenerationProvider> getProvider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    public VideoGenerationResult submit(String providerId, VideoGenerationRequest request) {
        VideoGenerationProvider provider = providers.get(providerId);
        if (provider == null) {
            return VideoGenerationResult.failed("unknown",
                    "No video generation provider found with ID: " + providerId);
        }
        return provider.submit(request);
    }

    public VideoGenerationResult poll(String providerId, String jobId) {
        VideoGenerationProvider provider = providers.get(providerId);
        if (provider == null) {
            return VideoGenerationResult.failed(jobId,
                    "No video generation provider found with ID: " + providerId);
        }
        return provider.poll(jobId);
    }

    public List<String> availableProviders() {
        return List.copyOf(providers.keySet());
    }
}
