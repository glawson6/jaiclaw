package io.jaiclaw.pipeline.dsl;

import io.jaiclaw.pipeline.PipelineSecurityProperties;

/**
 * Fluent builder for {@link PipelineSecurityProperties}. Chains back to the parent {@link PipelineBuilder}.
 */
public class SecurityBuilder {

    private final PipelineBuilder parent;
    private boolean enabled = true;
    private boolean requireAuthentication;
    private boolean enforceTenantIsolation;
    private boolean validateStageInputs;
    private int maxOutputSizeBytes = 1_048_576;
    private boolean auditSecurityEvents = true;

    SecurityBuilder(PipelineBuilder parent) {
        this.parent = parent;
    }

    /**
     * Require JWT/API-key authentication for HTTP-triggered pipelines.
     *
     * @return the parent pipeline builder
     */
    public PipelineBuilder requireAuthentication() {
        this.requireAuthentication = true;
        return parent;
    }

    /**
     * Enforce strict tenant isolation — reject mismatched tenants.
     *
     * @return the parent pipeline builder
     */
    public PipelineBuilder enforceTenantIsolation() {
        this.enforceTenantIsolation = true;
        return parent;
    }

    /**
     * Enable prompt injection detection for agent stage inputs.
     *
     * @return the parent pipeline builder
     */
    public PipelineBuilder validateStageInputs() {
        this.validateStageInputs = true;
        return parent;
    }

    /**
     * Set maximum stage output size in bytes.
     *
     * @param maxBytes the maximum output size
     * @return the parent pipeline builder
     */
    public PipelineBuilder maxOutputSizeBytes(int maxBytes) {
        this.maxOutputSizeBytes = maxBytes;
        return parent;
    }

    /**
     * Control whether security events are logged to AuditLogger.
     *
     * @param audit true to enable audit logging
     * @return the parent pipeline builder
     */
    public PipelineBuilder auditSecurityEvents(boolean audit) {
        this.auditSecurityEvents = audit;
        return parent;
    }

    PipelineSecurityProperties build() {
        return new PipelineSecurityProperties(
                enabled, requireAuthentication, enforceTenantIsolation,
                validateStageInputs, maxOutputSizeBytes, auditSecurityEvents
        );
    }
}
