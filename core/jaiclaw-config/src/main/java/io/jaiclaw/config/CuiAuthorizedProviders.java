package io.jaiclaw.config;

/**
 * Federal compliance (2026-08): resolver for CUI-authorized provider
 * status (CMMC L2 / DFARS 252.204-7012).
 *
 * <p>Like {@link FedRampAuthorizedProviders}, no baked-in defaults.
 * CUI authorization depends on the specific DoD contract and enclave
 * boundary. Operators explicitly declare authorization per provider
 * via {@code jaiclaw.models.providers.<name>.cui-authorized=true}.
 *
 * <p>Consumed by {@code CuiWarningChatModelDecorator}.
 */
public final class CuiAuthorizedProviders {

    private CuiAuthorizedProviders() {}

    /**
     * Resolve CUI-authorized status for a provider. Precedence: (1)
     * explicit config value if set, (2) false (unknown = not authorized).
     */
    public static boolean resolve(String providerName, ModelsProperties.ModelProviderConfig config) {
        if (config != null && config.cuiAuthorized() != null) {
            return config.cuiAuthorized();
        }
        return false;
    }
}
