package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

import java.time.Instant;
import java.util.List;

/**
 * T3-4 — SPI for exporting processor + sub-processor metadata for a Data
 * Processing Agreement.
 *
 * <p>Adopters use the exported {@link ProcessorMetadata} to auto-fill a DPA
 * template (e.g. a Word doc from legal, a PDF form, a Notion page). The
 * framework doesn't render the DPA itself — that's the operator's chosen
 * template.
 *
 * <p>Contract:
 * <ul>
 *   <li>Export MUST be tenant-scoped where relevant — the retention values,
 *       sub-processor list, and security measures may differ per tenant.</li>
 *   <li>The {@code securityMeasures} list SHOULD reflect the currently-enabled
 *       compliance profile (e.g. "at-rest encryption via AES-GCM" only if
 *       {@code EncryptedTranscriptStore} is wired).</li>
 * </ul>
 */
@Stable
public interface ProcessorMetadataExporter {

    /**
     * Export the metadata needed to fill a DPA for {@code tenantId}.
     */
    ProcessorMetadata exportForTenant(String tenantId);

    /**
     * @param processorName        the JaiClaw deployment identifier
     * @param processorContactEmail contact email for DPA questions
     * @param generatedAt          when the metadata was assembled
     * @param subProcessors        cloud providers + LLM providers + third
     *                             parties that receive data
     * @param retentionSummary     summary text describing per-tenant retention
     * @param securityMeasures     enabled hardening / compliance measures
     */
    record ProcessorMetadata(
            String processorName,
            String processorContactEmail,
            Instant generatedAt,
            List<SubProcessor> subProcessors,
            String retentionSummary,
            List<String> securityMeasures
    ) {
        public ProcessorMetadata {
            if (processorName == null || processorName.isBlank())
                throw new IllegalArgumentException("processorName must not be blank");
            if (generatedAt == null) generatedAt = Instant.now();
            subProcessors = subProcessors == null ? List.of() : List.copyOf(subProcessors);
            securityMeasures = securityMeasures == null ? List.of() : List.copyOf(securityMeasures);
        }
    }

    /**
     * A single external data recipient — LLM provider, cloud vendor, IdP.
     *
     * @param name        provider display name
     * @param purpose     what the framework uses it for
     * @param dataClasses categories of data sent (e.g. {@code "user_utterance"})
     * @param location    processing region (e.g. {@code "us-east-1"})
     * @param baaEligible whether the provider is BAA-eligible (T1-4 catalog)
     */
    record SubProcessor(
            String name, String purpose, List<String> dataClasses,
            String location, boolean baaEligible
    ) {}
}
