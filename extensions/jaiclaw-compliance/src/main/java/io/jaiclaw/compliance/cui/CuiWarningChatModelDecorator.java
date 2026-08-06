package io.jaiclaw.compliance.cui;

import io.jaiclaw.config.CuiAuthorizedProviders;
import io.jaiclaw.config.LlmConfig;
import io.jaiclaw.config.ModelsProperties;
import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Federal compliance (2026-08): CUI-authorized-provider warning check
 * (CMMC L2 / DFARS 252.204-7012). Mirrors {@code BaaWarningChatModelDecorator}
 * and {@code FedRampWarningChatModelDecorator} in shape.
 *
 * <p>Fires on model resolution when the compliance module is loaded and
 * {@code jaiclaw.compliance.effective.cui-warnings=true}. Emits a WARN
 * when the tenant is marked {@code cui.processing=true} and the resolved
 * provider is not marked
 * {@code jaiclaw.models.providers.<name>.cui-authorized=true}.
 *
 * <p>Adopters requiring hard refusal (throw) subclass this decorator.
 *
 * <p>See {@link CuiAuthorizedProviders} for the resolution rule.
 */
public class CuiWarningChatModelDecorator {

    private static final Logger log = LoggerFactory.getLogger(CuiWarningChatModelDecorator.class);

    private final ModelsProperties modelsProperties;

    public CuiWarningChatModelDecorator(ModelsProperties modelsProperties) {
        this.modelsProperties = modelsProperties;
    }

    /**
     * Called by the compliance auto-config when a {@code ChatModel} is
     * about to be created for a tenant. Emits a WARN when the tenant is
     * marked {@code cui.processing=true} and the resolved provider isn't
     * CUI-authorized.
     */
    public void check(TenantAgentConfig config) {
        if (config == null || config.llm() == null) return;
        LlmConfig llm = config.llm();
        String provider = llm.provider();
        ModelsProperties.ModelProviderConfig providerConfig = null;
        if (provider != null && modelsProperties != null && modelsProperties.providers() != null) {
            providerConfig = modelsProperties.providers().get(provider);
        }

        TenantContext ctx = TenantContextHolder.get();
        boolean cui = ctx != null && ctx.isCuiProcessing();
        if (!cui) return;
        if (CuiAuthorizedProviders.resolve(provider, providerConfig)) return;

        log.warn(
                "CMMC/CUI warning: tenant '{}' is marked cui.processing=true but " +
                "provider '{}' is not marked CUI-authorized in this deployment. " +
                "See docs/compliance/cmmc.md, or set " +
                "jaiclaw.models.providers.{}.cui-authorized=true if this provider " +
                "is within your CUI boundary.",
                config.tenantId(), provider, provider);
    }
}
