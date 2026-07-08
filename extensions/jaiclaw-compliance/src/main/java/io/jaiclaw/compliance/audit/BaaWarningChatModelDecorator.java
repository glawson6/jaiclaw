package io.jaiclaw.compliance.audit;

import io.jaiclaw.config.BaaEligibleProviders;
import io.jaiclaw.config.LlmConfig;
import io.jaiclaw.config.ModelsProperties;
import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BAA-eligible provider warning check, extracted from
 * {@code DefaultTenantChatModelFactory} so it only runs when the
 * compliance module is loaded and {@code jaiclaw.compliance.effective.baa-warnings}
 * is true.
 *
 * <p>The check is fired by a listener wired in {@link
 * io.jaiclaw.compliance.JaiClawComplianceAutoConfiguration}. It is not
 * a decorator on the actual ChatModel — the audit is a passive log
 * statement, so intercepting the model itself is unnecessary. We just
 * need to be called during model resolution.
 */
public class BaaWarningChatModelDecorator {

    private static final Logger log = LoggerFactory.getLogger(BaaWarningChatModelDecorator.class);

    private final ModelsProperties modelsProperties;

    public BaaWarningChatModelDecorator(ModelsProperties modelsProperties) {
        this.modelsProperties = modelsProperties;
    }

    /**
     * Called by the compliance auto-config when a {@code ChatModel} is
     * about to be created for a tenant. Emits a WARN when the tenant is
     * marked {@code phi_processing=true} and the resolved provider isn't
     * BAA-eligible.
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
        boolean phi = ctx != null && ctx.isPhiProcessing();
        if (!phi) return;
        if (BaaEligibleProviders.resolve(provider, providerConfig)) return;
        log.warn(
                "HIPAA warning: tenant '{}' is marked hipaa.phi_processing=true but " +
                "provider '{}' is not BAA-eligible in this deployment. " +
                "See docs/user/COMPLIANCE.md § BAA-eligible providers, or set " +
                "jaiclaw.models.providers.{}.baa-eligible=true if you have a signed BAA " +
                "with this provider.",
                config.tenantId(), provider, provider);
    }
}
