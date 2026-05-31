package io.jaiclaw.video;

/**
 * Configuration for the video generation module.
 *
 * @param defaultProvider  default provider ID (e.g. "runway")
 * @param runwayApiKey     API key for the Runway provider
 * @param runwayModel      Runway model name (e.g. "gen3a_turbo")
 */
public record VideoConfig(
        String defaultProvider,
        String runwayApiKey,
        String runwayModel
) {
    public VideoConfig {
        if (defaultProvider == null) defaultProvider = "runway";
        if (runwayModel == null) runwayModel = "gen3a_turbo";
    }

    public static final VideoConfig DEFAULT = new VideoConfig("runway", null, "gen3a_turbo");

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String defaultProvider = "runway";
        private String runwayApiKey;
        private String runwayModel = "gen3a_turbo";

        public Builder defaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; return this; }
        public Builder runwayApiKey(String runwayApiKey) { this.runwayApiKey = runwayApiKey; return this; }
        public Builder runwayModel(String runwayModel) { this.runwayModel = runwayModel; return this; }

        public VideoConfig build() {
            return new VideoConfig(defaultProvider, runwayApiKey, runwayModel);
        }
    }
}
