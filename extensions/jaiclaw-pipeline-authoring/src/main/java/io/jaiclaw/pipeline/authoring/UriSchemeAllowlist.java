package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.pipeline.OutputDefinition;
import io.jaiclaw.pipeline.OutputType;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineSecurityProperties;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.StageType;
import io.jaiclaw.pipeline.TriggerDefinition;
import io.jaiclaw.pipeline.TriggerType;
import io.jaiclaw.pipeline.validation.ValidationError;
import io.jaiclaw.pipeline.validation.ValidationReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Guard for the Camel URI-scheme allowlist described in
 * {@link PipelineSecurityProperties#allowedUriSchemes()}. Applied only
 * to UI-origin drafts (per the Phase 3 authz design) — hand-authored
 * YAML and code beans bypass the check.
 *
 * <p>Walks {@code stage.uri()} for CAMEL stages, {@code trigger.uri()}
 * when the trigger is CAMEL_URI, and {@code output.uri()} when the
 * output is CAMEL_URI. Extracts the scheme (everything up to the
 * first {@code :}) and rejects it if not in the allowlist.
 */
public class UriSchemeAllowlist {

    private final Set<String> allowedSchemes;

    public UriSchemeAllowlist(Set<String> allowedSchemes) {
        this.allowedSchemes = allowedSchemes == null
                ? PipelineSecurityProperties.DEFAULT_ALLOWED_URI_SCHEMES
                : allowedSchemes;
    }

    /**
     * Return a report with one error per disallowed URI. Empty when the
     * definition is null, the definition's origin is not UI, or every
     * URI scheme is on the allowlist.
     */
    public ValidationReport check(PipelineDefinition definition, PipelineDraft.Origin origin) {
        ValidationReport.Builder report = new ValidationReport.Builder();
        if (definition == null) return report.build();
        if (origin != PipelineDraft.Origin.STUDIO) return report.build();
        if (allowedSchemes.isEmpty()) return report.build();

        String pipelineId = definition.id();

        TriggerDefinition trigger = definition.trigger();
        if (trigger != null && trigger.type() == TriggerType.CAMEL_URI) {
            checkUri(pipelineId, "trigger", trigger.uri(), report);
        }

        for (StageDefinition stage : definition.stages() == null ? List.<StageDefinition>of() : definition.stages()) {
            if (stage.type() == StageType.CAMEL) {
                checkUri(pipelineId, "stage '" + stage.name() + "'", stage.uri(), report);
            }
            if (stage.transport() != null) {
                checkUri(pipelineId, "stage '" + stage.name() + "' transport",
                        stage.transport().uri(), report);
            }
        }

        OutputDefinition output = definition.output();
        if (output != null && output.type() == OutputType.CAMEL_URI) {
            checkUri(pipelineId, "output", output.uri(), report);
        }

        return report.build();
    }

    private void checkUri(String pipelineId, String location, String uri,
                          ValidationReport.Builder report) {
        if (uri == null || uri.isBlank()) return;
        int colon = uri.indexOf(':');
        if (colon <= 0) {
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId, location, "MALFORMED_URI",
                    "Camel URI '" + uri + "' has no scheme (expected {scheme}:...)",
                    null));
            return;
        }
        String scheme = uri.substring(0, colon).toLowerCase();
        if (!allowedSchemes.contains(scheme)) {
            List<String> sorted = new ArrayList<>(new TreeSet<>(allowedSchemes));
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId, location, "URI_SCHEME_DENIED",
                    "URI scheme '" + scheme + "' is not on the Studio allowlist "
                            + "(allowed: " + sorted + "). "
                            + "Adopters extend the list via "
                            + "jaiclaw.pipeline.authoring.security.allowed-uri-schemes.",
                    null));
        }
    }

    /** True when the given URI's scheme is on the allowlist. */
    public boolean isAllowed(String uri) {
        if (uri == null || uri.isBlank()) return true;
        int colon = uri.indexOf(':');
        if (colon <= 0) return false;
        String scheme = uri.substring(0, colon).toLowerCase();
        return allowedSchemes.contains(scheme);
    }

    public Set<String> allowedSchemes() { return allowedSchemes; }
}
