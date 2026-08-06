package io.jaiclaw.config;

/**
 * Federal compliance (2026-08): resolver for FedRAMP-authorized provider status.
 *
 * <p>Unlike {@link BaaEligibleProviders}, FedRAMP does <b>NOT</b> ship
 * with baked-in defaults. FedRAMP authorization is deployment-specific
 * (a provider might be authorized for one agency's boundary but not
 * another; a provider might be authorized only for Low but not for
 * Moderate). The safe default for federal use is "unknown = not
 * authorized" — operators explicitly declare authorization per provider
 * via {@code jaiclaw.models.providers.<name>.fedramp-authorized=true}.
 *
 * <p>Consumed by {@code FedRampWarningChatModelDecorator}.
 */
public final class FedRampAuthorizedProviders {

    private FedRampAuthorizedProviders() {}

    /**
     * Resolve FedRAMP-authorized status for a provider. Precedence: (1)
     * explicit config value if set, (2) false (unknown = not authorized).
     *
     * @param providerName provider identifier (e.g. "anthropic", "bedrock")
     * @param config       provider config (may be null)
     * @return true iff the provider is explicitly marked FedRAMP-authorized
     */
    public static boolean resolve(String providerName, ModelsProperties.ModelProviderConfig config) {
        if (config != null && config.fedrampAuthorized() != null) {
            return config.fedrampAuthorized();
        }
        return false;
    }
}
