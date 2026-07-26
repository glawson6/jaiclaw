package io.jaiclaw.pipeline;

import java.util.Set;

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
 * @param allowedUriSchemes       Camel URI schemes UI-origin definitions may use. Enforced by the
 *                                Pipeline Studio's URI-scheme allowlist (Phase 3 of the buildout).
 *                                Applied to {@link StageDefinition#uri()} on CAMEL stages, plus
 *                                {@link TriggerDefinition#uri()} and {@link OutputDefinition#uri()}
 *                                whenever the draft's origin is {@code STUDIO}. Non-UI-origin
 *                                definitions (per-file YAML, code beans) bypass the check — an
 *                                operator hand-editing YAML is already trusted with arbitrary
 *                                Camel URIs. Default: {@code direct, seda, log, vm, timer, quartz}.
 *                                An empty set disables the check entirely (not recommended when
 *                                the Studio is exposed to untrusted authors).
 */
public record PipelineSecurityProperties(
        boolean enabled,
        boolean requireAuthentication,
        boolean enforceTenantIsolation,
        boolean validateStageInputs,
        int maxOutputSizeBytes,
        boolean auditSecurityEvents,
        Set<String> allowedUriSchemes
) {
    /** Default schemes permitted for UI-origin pipelines. Safe: no network, no shell, no filesystem. */
    public static final Set<String> DEFAULT_ALLOWED_URI_SCHEMES =
            Set.of("direct", "seda", "log", "vm", "timer", "quartz");

    public static final PipelineSecurityProperties DEFAULT =
            new PipelineSecurityProperties(false, false, false, false, 1_048_576, true,
                    DEFAULT_ALLOWED_URI_SCHEMES);

    public PipelineSecurityProperties {
        if (maxOutputSizeBytes <= 0) maxOutputSizeBytes = 1_048_576;
        if (allowedUriSchemes == null) allowedUriSchemes = DEFAULT_ALLOWED_URI_SCHEMES;
        else allowedUriSchemes = Set.copyOf(allowedUriSchemes);
    }

    /**
     * Backward-compatible 6-arg constructor — omits {@code allowedUriSchemes},
     * which defaults to {@link #DEFAULT_ALLOWED_URI_SCHEMES}. Existing YAML
     * fixtures and Spock specs compile unchanged.
     */
    public PipelineSecurityProperties(
            boolean enabled,
            boolean requireAuthentication,
            boolean enforceTenantIsolation,
            boolean validateStageInputs,
            int maxOutputSizeBytes,
            boolean auditSecurityEvents) {
        this(enabled, requireAuthentication, enforceTenantIsolation,
                validateStageInputs, maxOutputSizeBytes, auditSecurityEvents,
                DEFAULT_ALLOWED_URI_SCHEMES);
    }
}
