package io.jaiclaw.compliance.fedramp;

import io.jaiclaw.config.FedRampAuthorizedProviders;
import io.jaiclaw.config.LlmConfig;
import io.jaiclaw.config.ModelsProperties;
import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Federal compliance (2026-08): FedRAMP-authorized-provider warning
 * check. Mirrors {@code BaaWarningChatModelDecorator} in shape.
 *
 * <p>Fires on model resolution when the compliance module is loaded and
 * {@code jaiclaw.compliance.effective.fedramp-warnings=true}. Emits a
 * WARN when the tenant is marked
 * {@code fedramp.impact_level=moderate|high} and the resolved provider
 * is not marked
 * {@code jaiclaw.models.providers.<name>.fedramp-authorized=true}.
 *
 * <p>Adopters requiring hard refusal (throw) subclass this decorator.
 * The default posture is passive audit (WARN log) — matches the shape
 * of the BAA warning so operators get consistent behavior.
 *
 * <p>See {@link FedRampAuthorizedProviders} for the resolution rule.
 */
public class FedRampWarningChatModelDecorator {

    private static final Logger log = LoggerFactory.getLogger(FedRampWarningChatModelDecorator.class);

    private final ModelsProperties modelsProperties;

    public FedRampWarningChatModelDecorator(ModelsProperties modelsProperties) {
        this.modelsProperties = modelsProperties;
    }

    /**
     * Called by the compliance auto-config when a {@code ChatModel} is
     * about to be created for a tenant. Emits a WARN when the tenant's
     * FedRAMP impact level is moderate or high and the resolved provider
     * isn't FedRAMP-authorized.
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
        if (ctx == null) return;
        String impact = ctx.getFedrampImpactLevel();
        if (impact == null) return;
        String normalized = impact.trim().toLowerCase();
        if (!"moderate".equals(normalized) && !"high".equals(normalized)) {
            return;
        }
        if (FedRampAuthorizedProviders.resolve(provider, providerConfig)) return;

        log.warn(
                "FedRAMP warning: tenant '{}' is marked fedramp.impact_level={} but " +
                "provider '{}' is not marked FedRAMP-authorized in this deployment. " +
                "See docs/compliance/fedramp.md, or set " +
                "jaiclaw.models.providers.{}.fedramp-authorized=true if your ATO covers " +
                "this provider.",
                config.tenantId(), normalized, provider, provider);
    }
}
