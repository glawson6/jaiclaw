package io.jaiclaw.pipeline;

/**
 * Stage processing type. Each type determines which {@link StageProcessor} handles the stage.
 */
public enum StageType {
    /** Delegates to a JaiClaw agent via {@link io.jaiclaw.camel.GatewayServiceAccessor}. */
    AGENT,
    /** Invokes a Spring bean implementing {@code Function<String, String>}. */
    PROCESSOR,
    /** Sends the exchange to an arbitrary Camel endpoint URI. */
    CAMEL
}
