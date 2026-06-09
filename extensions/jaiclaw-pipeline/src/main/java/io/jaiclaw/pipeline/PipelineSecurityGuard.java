package io.jaiclaw.pipeline;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Central security enforcement for pipeline execution. All checks are no-ops
 * when security is disabled ({@code enabled: false}).
 */
public class PipelineSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(PipelineSecurityGuard.class);

    /** Basic prompt injection patterns (case-insensitive). */
    private static final Pattern[] INJECTION_PATTERNS = {
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?prior\\s+(instructions|context)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+a"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are")
    };

    private final PipelineSecurityProperties securityProps;
    private final AuditLogger auditLogger;

    public PipelineSecurityGuard(PipelineSecurityProperties securityProps, AuditLogger auditLogger) {
        this.securityProps = securityProps != null ? securityProps : PipelineSecurityProperties.DEFAULT;
        this.auditLogger = auditLogger;
    }

    /**
     * Validate pipeline execution at startup. Checks authentication and tenant isolation.
     *
     * @param definition the pipeline definition
     * @param ctx        the pipeline context
     */
    public void validateExecution(PipelineDefinition definition, PipelineContext ctx) {
        if (!securityProps.enabled()) return;

        if (securityProps.requireAuthentication()) {
            validateAuthentication(definition, ctx);
        }

        if (securityProps.enforceTenantIsolation()) {
            validateTenantIsolation(definition, ctx);
        }
    }

    /**
     * Validate stage output size. Truncates if oversized and logs a warning.
     *
     * @param stageName the stage name
     * @param output    the stage output
     * @return the output, possibly truncated
     */
    public String validateStageOutput(String stageName, String output) {
        if (!securityProps.enabled() || output == null) return output;

        int maxBytes = securityProps.maxOutputSizeBytes();
        if (output.length() > maxBytes) {
            log.warn("Stage '{}' output exceeds max size ({} > {} bytes), truncating",
                    stageName, output.length(), maxBytes);
            return output.substring(0, maxBytes);
        }
        return output;
    }

    /**
     * Validate stage input for prompt injection patterns (agent stages only).
     *
     * @param stageName the stage name
     * @param input     the stage input
     * @param type      the stage type
     */
    public void validateStageInput(String stageName, String input, StageType type) {
        if (!securityProps.enabled() || !securityProps.validateStageInputs()) return;
        if (type != StageType.AGENT || input == null || input.isEmpty()) return;

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("Suspicious input detected in stage '{}': matches pattern '{}'",
                        stageName, pattern.pattern());

                if (auditLogger != null && securityProps.auditSecurityEvents()) {
                    auditLogger.log(AuditEvent.builder()
                            .id(UUID.randomUUID().toString())
                            .action("pipeline.security.suspicious_input")
                            .resource(stageName)
                            .outcome(AuditEvent.Outcome.DENIED)
                            .details(Map.of(
                                    "pattern", pattern.pattern(),
                                    "inputPreview", input.substring(0, Math.min(200, input.length()))
                            ))
                            .build());
                }
                break;
            }
        }
    }

    private void validateAuthentication(PipelineDefinition definition, PipelineContext ctx) {
        // Check if Spring Security context has an authenticated principal
        try {
            Class<?> securityContextHolderClass = Class.forName(
                    "org.springframework.security.core.context.SecurityContextHolder");
            Object securityContext = securityContextHolderClass.getMethod("getContext").invoke(null);
            Object authentication = securityContext.getClass().getMethod("getAuthentication").invoke(securityContext);

            if (authentication == null) {
                denyAndAudit(definition.id(), "No authentication present for pipeline execution");
            }

            boolean isAuthenticated = (boolean) authentication.getClass()
                    .getMethod("isAuthenticated").invoke(authentication);
            if (!isAuthenticated) {
                denyAndAudit(definition.id(), "Authentication is not authenticated");
            }
        } catch (ClassNotFoundException e) {
            // Spring Security not on classpath — skip check
            log.debug("Spring Security not available, skipping authentication check");
        } catch (PipelineSecurityException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check authentication: {}", e.getMessage());
        }
    }

    private void validateTenantIsolation(PipelineDefinition definition, PipelineContext ctx) {
        // Empty tenantIds means global pipeline — accessible to all tenants
        if (definition.tenantIds().isEmpty()) return;

        String callerTenant = ctx.tenantId();
        if (callerTenant == null || !definition.tenantIds().contains(callerTenant)) {
            denyAndAudit(definition.id(),
                    "Tenant '" + callerTenant + "' is not authorized for pipeline '" + definition.id() + "'");
        }
    }

    private void denyAndAudit(String pipelineId, String reason) {
        if (auditLogger != null && securityProps.auditSecurityEvents()) {
            auditLogger.log(AuditEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .action("pipeline.security.denied")
                    .resource(pipelineId)
                    .outcome(AuditEvent.Outcome.DENIED)
                    .details(Map.of("reason", reason))
                    .build());
        }
        throw new PipelineSecurityException(pipelineId, null, reason);
    }
}
