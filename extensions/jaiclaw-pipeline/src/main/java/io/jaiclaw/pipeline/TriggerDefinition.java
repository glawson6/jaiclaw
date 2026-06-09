package io.jaiclaw.pipeline;

/**
 * Defines how a pipeline execution is triggered.
 *
 * @param type       the trigger mechanism
 * @param uri        Camel URI for FILE or CAMEL_URI triggers (nullable)
 * @param expression cron expression for CRON triggers (nullable)
 * @param path       HTTP path for HTTP triggers (nullable)
 */
public record TriggerDefinition(
        TriggerType type,
        String uri,
        String expression,
        String path
) {
    public TriggerDefinition {
        if (type == null) type = TriggerType.MANUAL;
    }
}
