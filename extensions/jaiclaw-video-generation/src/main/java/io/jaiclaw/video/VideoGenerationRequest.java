package io.jaiclaw.video;

import java.util.Map;

/**
 * Request for video generation.
 *
 * @param prompt       text description of the video to generate
 * @param imageUrl     optional reference image URL (for image-to-video)
 * @param durationSecs desired video duration in seconds
 * @param resolution   desired output resolution (e.g. "1280x768")
 * @param options      provider-specific options
 */
public record VideoGenerationRequest(
        String prompt,
        String imageUrl,
        int durationSecs,
        String resolution,
        Map<String, String> options
) {
    public VideoGenerationRequest {
        if (prompt == null) prompt = "";
        if (durationSecs <= 0) durationSecs = 5;
        if (resolution == null) resolution = "1280x768";
        if (options == null) options = Map.of();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String prompt;
        private String imageUrl;
        private int durationSecs = 5;
        private String resolution = "1280x768";
        private Map<String, String> options = Map.of();

        public Builder prompt(String prompt) { this.prompt = prompt; return this; }
        public Builder imageUrl(String imageUrl) { this.imageUrl = imageUrl; return this; }
        public Builder durationSecs(int durationSecs) { this.durationSecs = durationSecs; return this; }
        public Builder resolution(String resolution) { this.resolution = resolution; return this; }
        public Builder options(Map<String, String> options) { this.options = options; return this; }

        public VideoGenerationRequest build() {
            return new VideoGenerationRequest(prompt, imageUrl, durationSecs, resolution, options);
        }
    }
}
