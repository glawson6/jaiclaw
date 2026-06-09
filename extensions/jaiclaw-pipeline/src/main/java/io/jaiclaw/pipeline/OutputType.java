package io.jaiclaw.pipeline;

/**
 * How the final pipeline output is delivered.
 */
public enum OutputType {
    /** Send to a JaiClaw channel. */
    CHANNEL,
    /** Send to an arbitrary Camel endpoint URI. */
    CAMEL_URI,
    /** Log the output. */
    LOG,
    /** Discard the output. */
    NONE
}
