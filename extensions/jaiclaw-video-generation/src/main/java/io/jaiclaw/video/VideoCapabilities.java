package io.jaiclaw.video;

import java.util.List;

/**
 * Capabilities of a video generation provider.
 *
 * @param supportedResolutions supported output resolutions (e.g. "1280x768")
 * @param maxDurationSeconds   maximum video duration in seconds
 * @param supportsImageInput   whether the provider supports image-to-video generation
 * @param supportsTextInput    whether the provider supports text-to-video generation
 */
public record VideoCapabilities(
        List<String> supportedResolutions,
        int maxDurationSeconds,
        boolean supportsImageInput,
        boolean supportsTextInput
) {
    public VideoCapabilities {
        if (supportedResolutions == null) supportedResolutions = List.of();
    }
}
