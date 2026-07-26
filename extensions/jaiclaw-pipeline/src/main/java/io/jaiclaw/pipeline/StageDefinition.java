package io.jaiclaw.pipeline;

import java.time.Duration;
import java.util.Map;

/**
 * Defines a single stage within a pipeline.
 *
 * @param name            unique stage name within the pipeline
 * @param type            processing type (AGENT, PROCESSOR, CAMEL)
 * @param bean            Spring bean name for PROCESSOR stages (nullable)
 * @param agentId         agent identifier for AGENT stages (nullable)
 * @param systemPrompt    system prompt template for AGENT stages
 *                        (nullable, supports {{stages.X.output}})
 * @param channelId       channel ID for AGENT stages
 *                        (default: "pipeline-internal")
 * @param uri             Camel endpoint URI for CAMEL stages (nullable)
 * @param timeout         stage execution timeout (nullable)
 * @param transport       inter-stage transport configuration override
 *                        (nullable — uses default SEDA)
 * @param runtime         AGENT-stage runtime selector
 *                        ({@link StageRuntime#NATIVE} default, or
 *                        {@link StageRuntime#EMBABEL} to route through
 *                        Embabel's GOAP planner). Defaults to
 *                        {@code NATIVE} if {@code null}.
 * @param embabelWorkflow Embabel agent name to invoke when
 *                        {@code runtime == EMBABEL}. Required for that
 *                        runtime; ignored otherwise.
 * @param config          per-stage configuration for PROCESSOR stages
 *                        whose bean implements
 *                        {@link ConfigurableStageProcessor}. The map
 *                        is passed to
 *                        {@link ConfigurableStageProcessor#process}. Ignored
 *                        for AGENT/CAMEL stages and for bare
 *                        {@code Function<String,String>} beans.
 *                        Nullable — defaults to an empty map.
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
        TransportConfig transport,
        StageRuntime runtime,
        String embabelWorkflow,
        Map<String, String> config
) {
    public StageDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Stage name must not be blank");
        if (type == null) type = StageType.PROCESSOR;
        if (channelId == null || channelId.isBlank()) channelId = "pipeline-internal";
        if (runtime == null) runtime = StageRuntime.NATIVE;
        if (runtime == StageRuntime.EMBABEL
                && (embabelWorkflow == null || embabelWorkflow.isBlank())) {
            throw new IllegalArgumentException(
                    "Stage '" + name + "': runtime=EMBABEL requires a non-blank embabelWorkflow");
        }
        if (config == null) config = Map.of();
        else config = Map.copyOf(config);
    }

    /**
     * Backward-compatible 11-arg constructor — omits {@code config},
     * which defaults to an empty map. Retained so existing per-file
     * YAML fixtures and Spock specs compile unchanged.
     */
    public StageDefinition(
            String name,
            StageType type,
            String bean,
            String agentId,
            String systemPrompt,
            String channelId,
            String uri,
            Duration timeout,
            TransportConfig transport,
            StageRuntime runtime,
            String embabelWorkflow) {
        this(name, type, bean, agentId, systemPrompt, channelId, uri, timeout,
                transport, runtime, embabelWorkflow, Map.of());
    }

    /**
     * Backward-compatible 9-arg constructor — delegates to the canonical
     * 12-arg form with {@code runtime=NATIVE}, {@code embabelWorkflow=null},
     * and an empty {@code config} map. Existing callers (Spock specs,
     * hand-rolled stages) continue to compile unchanged.
     */
    public StageDefinition(
            String name,
            StageType type,
            String bean,
            String agentId,
            String systemPrompt,
            String channelId,
            String uri,
            Duration timeout,
            TransportConfig transport) {
        this(name, type, bean, agentId, systemPrompt, channelId, uri, timeout, transport,
                StageRuntime.NATIVE, null, Map.of());
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
