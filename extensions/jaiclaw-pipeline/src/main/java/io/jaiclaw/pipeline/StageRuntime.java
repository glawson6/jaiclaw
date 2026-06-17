package io.jaiclaw.pipeline;

/**
 * Selects the execution runtime for an AGENT-type pipeline stage.
 *
 * <p>{@link #NATIVE} (the default) routes the stage through the JaiClaw
 * agent loop via {@code GatewayServiceAccessor.handleSync(...)} — the
 * same LLM+tool loop that serves in-message agents. Tool selection is
 * LLM-sampled and therefore non-deterministic across runs.
 *
 * <p>{@link #EMBABEL} routes the stage through Embabel's GOAP planner
 * via the {@code AgentOrchestrationPort} SPI. Action chains are derived
 * deterministically from typed pre/postconditions on the
 * {@code @Agent}-annotated class, so re-running the same input produces
 * the same action sequence. Requires {@code jaiclaw-starter-embabel}
 * on the classpath; {@code PipelineValidator} fails fast at startup
 * otherwise.
 */
public enum StageRuntime {
    NATIVE,
    EMBABEL
}
