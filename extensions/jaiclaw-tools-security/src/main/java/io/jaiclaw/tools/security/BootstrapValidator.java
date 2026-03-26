package io.jaiclaw.tools.security;

import java.util.Map;

/**
 * Validates bootstrap credentials during the initial handshake negotiate step.
 * Each {@link BootstrapTrust} level has a corresponding implementation.
 */
public interface BootstrapValidator {

    /**
     * Validate the bootstrap credentials provided in the negotiate request.
     *
     * @param negotiateParams the parameters from the negotiate request
     * @param properties      the handshake configuration
     * @return true if the bootstrap credentials are valid
     */
    boolean validate(Map<String, Object> negotiateParams, SecurityHandshakeProperties properties);

    /**
     * A human-readable description of why validation failed (for error messages).
     */
    default String failureReason() {
        return "Bootstrap authentication failed";
    }
}
