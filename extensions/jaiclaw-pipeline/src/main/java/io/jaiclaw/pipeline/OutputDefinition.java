package io.jaiclaw.pipeline;

/**
 * Defines how the final pipeline output is delivered.
 *
 * @param type      the output delivery mechanism
 * @param channelId JaiClaw channel ID for CHANNEL output (nullable)
 * @param uri       Camel URI for CAMEL_URI output (nullable)
 * @param template  output template with {@code {{stages.X.output}}} placeholders (nullable)
 */
public record OutputDefinition(
        OutputType type,
        String channelId,
        String uri,
        String template
) {
    public OutputDefinition {
        if (type == null) type = OutputType.NONE;
    }
}
