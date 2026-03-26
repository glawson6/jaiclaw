package io.jaiclaw.tools.security;

import java.util.Map;

/**
 * Bootstrap validator for {@link BootstrapTrust#MUTUAL} level.
 * Combines API key validation (client proves knowledge of shared secret)
 * with client key verification. The server side of mutual authentication
 * (server proving its identity) happens later in the handshake when the
 * server signs its nonce.
 */
public class MutualBootstrapValidator implements BootstrapValidator {

    private final ApiKeyBootstrapValidator apiKeyValidator;
    private final ClientCertBootstrapValidator clientCertValidator;
    private String lastFailureReason;

    public MutualBootstrapValidator(CryptoService cryptoService) {
        this.apiKeyValidator = new ApiKeyBootstrapValidator();
        this.clientCertValidator = new ClientCertBootstrapValidator(cryptoService);
    }

    @Override
    public boolean validate(Map<String, Object> negotiateParams, SecurityHandshakeProperties properties) {
        // API key must be valid
        if (!apiKeyValidator.validate(negotiateParams, properties)) {
            lastFailureReason = apiKeyValidator.failureReason();
            return false;
        }

        // If client keys are configured, also validate client certificate
        if (properties.allowedClientKeys() != null && !properties.allowedClientKeys().isEmpty()) {
            if (!clientCertValidator.validate(negotiateParams, properties)) {
                lastFailureReason = clientCertValidator.failureReason();
                return false;
            }
        }

        return true;
    }

    @Override
    public String failureReason() {
        return lastFailureReason != null ? lastFailureReason : "Mutual authentication failed";
    }
}
