package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.config.BaaEligibleProviders;
import io.jaiclaw.config.ModelsProperties;
import io.jaiclaw.core.gdpr.ProcessorMetadataExporter;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * T3-4 reference {@link ProcessorMetadataExporter} — builds a DPA-ready
 * metadata record from ModelsProperties + the compliance module's currently-
 * enabled security measures. Adopters can subclass to layer on cloud-vendor
 * sub-processors that live outside JaiClaw's config (Postgres provider, CDN,
 * observability, etc.).
 *
 * <p>The security-measures list is built dynamically from the effective
 * compliance flags so an operator running with encryption + chain-of-hashes
 * on sees "AES-GCM at-rest encryption" + "SHA-256 chain-of-hashes audit log"
 * in the exported DPA.
 */
public class DefaultProcessorMetadataExporter implements ProcessorMetadataExporter {

    private final String processorName;
    private final String processorContactEmail;
    private final ModelsProperties modelsProperties;
    private final List<String> baseSecurityMeasures;
    private final Clock clock;

    public DefaultProcessorMetadataExporter(String processorName, String processorContactEmail,
                                             ModelsProperties modelsProperties,
                                             List<String> baseSecurityMeasures) {
        this(processorName, processorContactEmail, modelsProperties, baseSecurityMeasures, Clock.systemUTC());
    }

    public DefaultProcessorMetadataExporter(String processorName, String processorContactEmail,
                                             ModelsProperties modelsProperties,
                                             List<String> baseSecurityMeasures,
                                             Clock clock) {
        if (processorName == null || processorName.isBlank())
            throw new IllegalArgumentException("processorName must not be blank");
        this.processorName = processorName;
        this.processorContactEmail = processorContactEmail == null ? "" : processorContactEmail;
        this.modelsProperties = modelsProperties;
        this.baseSecurityMeasures = baseSecurityMeasures == null
                ? List.of() : List.copyOf(baseSecurityMeasures);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public ProcessorMetadata exportForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId must not be blank");
        List<SubProcessor> subProcessors = subProcessorsFromModels();
        String retentionSummary = "Per-tenant retention via TenantContext.getRetentionDays() and "
                + "RetentionEnforcementService (T1-6); unlimited when unset.";
        List<String> securityMeasures = new ArrayList<>(baseSecurityMeasures);
        // Common baseline measures always present in the framework.
        if (securityMeasures.isEmpty()) {
            securityMeasures.add("Multi-tenant isolation (TenantGuard)");
            securityMeasures.add("Structured audit trail with GDPR / HIPAA fields");
            securityMeasures.add("Optional encryption at rest via AES-GCM (T2-4)");
            securityMeasures.add("Optional chain-of-hashes audit log (T2-6)");
            securityMeasures.add("Configurable HTTPS-required startup guard (T1-7)");
        }
        return new ProcessorMetadata(processorName, processorContactEmail, clock.instant(),
                subProcessors, retentionSummary, securityMeasures);
    }

    private List<SubProcessor> subProcessorsFromModels() {
        List<SubProcessor> out = new ArrayList<>();
        if (modelsProperties == null || modelsProperties.providers() == null) return out;
        for (var entry : modelsProperties.providers().entrySet()) {
            String name = entry.getKey();
            ModelsProperties.ModelProviderConfig cfg = entry.getValue();
            boolean baa = BaaEligibleProviders.resolve(name, cfg);
            out.add(new SubProcessor(
                    name,
                    "LLM inference",
                    List.of("user_utterance", "system_prompt", "assistant_response"),
                    cfg == null || cfg.baseUrl() == null ? "unspecified" : cfg.baseUrl(),
                    baa));
        }
        return out;
    }
}
