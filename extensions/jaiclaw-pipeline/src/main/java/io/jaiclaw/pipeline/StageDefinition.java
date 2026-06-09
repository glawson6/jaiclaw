package io.jaiclaw.pipeline;

import java.time.Duration;

/**
 * Defines a single stage within a pipeline.
 *
 * @param name         unique stage name within the pipeline
 * @param type         processing type (AGENT, PROCESSOR, CAMEL)
 * @param bean         Spring bean name for PROCESSOR stages (nullable)
 * @param agentId      agent identifier for AGENT stages (nullable)
 * @param systemPrompt system prompt template for AGENT stages (nullable, supports {{stages.X.output}})
 * @param channelId    channel ID for AGENT stages (default: "pipeline-internal")
 * @param uri          Camel endpoint URI for CAMEL stages (nullable)
 * @param timeout      stage execution timeout (nullable)
 * @param transport    inter-stage transport configuration override (nullable — uses default SEDA)
 */
public record StageDefinition(
        String name,
        StageType type,
        String bean,
        String agentId,
        String systemPrompt,
        String channelId,
        String uri,
        Duration timeout,
        TransportConfig transport
) {
    public StageDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Stage name must not be blank");
        if (type == null) type = StageType.PROCESSOR;
        if (channelId == null || channelId.isBlank()) channelId = "pipeline-internal";
    }

    /**
     * Inter-stage transport configuration, allowing per-stage URI override
     * (e.g., {@code kafka:my-topic}) with optional authentication.
     *
     * @param uri  the transport URI (e.g., "kafka:raw-events?brokers=kafka:9092")
     * @param auth authentication configuration for verifying inbound messages (nullable)
     */
    public record TransportConfig(
            String uri,
            TransportAuth auth
    ) {
        public TransportConfig {
            if (uri == null || uri.isBlank()) throw new IllegalArgumentException("Transport URI must not be blank");
        }
    }

    /**
     * Authentication configuration for inter-stage transport verification.
     *
     * @param authType   the authentication method
     * @param secret     the shared secret for HMAC signing or bearer token comparison
     * @param headerName which header carries the token/signature
     */
    public record TransportAuth(
            TransportAuthType authType,
            String secret,
            String headerName
    ) {
        public TransportAuth {
            if (authType == null) authType = TransportAuthType.NONE;
            if (headerName == null || headerName.isBlank()) {
                headerName = switch (authType) {
                    case HMAC_SHA256 -> "X-Hub-Signature-256";
                    case BEARER_TOKEN -> "X-Pipeline-Token";
                    case NONE -> "";
                };
            }
        }
    }
}
