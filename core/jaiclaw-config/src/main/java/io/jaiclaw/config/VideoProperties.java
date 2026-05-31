package io.jaiclaw.config;

/**
 * Configuration properties for video generation, bound from {@code jaiclaw.video.*}.
 *
 * @param defaultProvider default video generation provider ID (e.g. "runway")
 * @param runwayApiKey    Runway API key
 * @param runwayModel     Runway model name (e.g. "gen3a_turbo")
 */
public record VideoProperties(
        String defaultProvider,
        String runwayApiKey,
        String runwayModel
) {
    public VideoProperties {
        if (defaultProvider == null) defaultProvider = "runway";
        if (runwayModel == null) runwayModel = "gen3a_turbo";
    }

    public static final VideoProperties DEFAULT = new VideoProperties(
            "runway", null, "gen3a_turbo");
}
