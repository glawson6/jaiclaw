package io.jaiclaw.channel.signal;

/**
 * Signal adapter integration modes.
 */
public enum SignalMode {
    /** Gateway manages a signal-cli daemon process with JSON-RPC over TCP. */
    EMBEDDED,
    /** Connects to an external signal-cli-rest-api sidecar via HTTP. */
    HTTP_CLIENT
}
