package io.jaiclaw.pipeline;

/**
 * Security configuration for pipeline execution. All features default to off,
 * following the security-hardened profile pattern.
 *
 * @param enabled                 master security switch (default: false)
 * @param requireAuthentication   require JWT/API-key auth for HTTP-triggered pipelines (default: false)
 * @param enforceTenantIsolation  reject execution if caller's tenant doesn't match pipeline's tenantIds (default: false)
 * @param validateStageInputs     sanitize inter-stage data for prompt injection in agent stages (default: false)
 * @param maxOutputSizeBytes      cap stage output size to prevent memory exhaustion (default: 1MB)
 * @param auditSecurityEvents     log security-related denials to AuditLogger (default: true when security is enabled)
 */
public record PipelineSecurityProperties(
        boolean enabled,
        boolean requireAuthentication,
        boolean enforceTenantIsolation,
        boolean validateStageInputs,
        int maxOutputSizeBytes,
        boolean auditSecurityEvents
) {
    public static final PipelineSecurityProperties DEFAULT =
            new PipelineSecurityProperties(false, false, false, false, 1_048_576, true);

    public PipelineSecurityProperties {
        if (maxOutputSizeBytes <= 0) maxOutputSizeBytes = 1_048_576;
    }
}
