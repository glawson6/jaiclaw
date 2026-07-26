package io.jaiclaw.pipeline;

import java.util.List;

/**
 * Complete definition of a pipeline — its identity, trigger, stages, output, and security.
 *
 * @param id             unique pipeline identifier
 * @param name           human-readable pipeline name (nullable)
 * @param description    pipeline description (nullable)
 * @param tenantIds      tenant IDs that may execute this pipeline (empty = all tenants)
 * @param enabled        whether the pipeline is active (default: true)
 * @param trigger        how the pipeline is triggered
 * @param errorStrategy  how stage failures are handled (default: STOP)
 * @param maxRetries     max retries for RETRY_THEN_FAIL strategy (default: 3)
 * @param deadLetterUri  Camel URI for DEAD_LETTER strategy (nullable)
 * @param stages         ordered list of stage definitions
 * @param output         final output delivery configuration
 * @param security       per-pipeline security overrides (nullable — uses global defaults)
 * @param resultTemplate template used to produce the caller-visible result
 *                       string on successful completion (nullable). Supports
 *                       {@code {{stages.<name>.output}}}, {@code
 *                       {{stages.<name>.metadata.<key>}}}, {@code {{input}}},
 *                       and {@code {{pipeline.*}}}. Ignored if a stage bean
 *                       has already written {@link PipelineResult#RESULT_KEY}
 *                       to the exchange — stage-write wins. See
 *                       {@link PipelineResult}.
 */
public record PipelineDefinition(
        String id,
        String name,
        String description,
        List<String> tenantIds,
        boolean enabled,
        TriggerDefinition trigger,
        ErrorStrategy errorStrategy,
        int maxRetries,
        String deadLetterUri,
        List<StageDefinition> stages,
        OutputDefinition output,
        PipelineSecurityProperties security,
        String resultTemplate
) {
    public PipelineDefinition {
        // A blank id is a legal *value* — the compact constructor coalesces
        // nulls to defaults but does not throw, so the record can round-trip
        // through Jackson for endpoints like POST /api/pipeline-studio/validate
        // that need to *report* on invalid input rather than crash on it.
        // "Blank id is not registerable" is enforced at the boundary:
        // PipelineRegistry.register, PipelineProperties YAML loader, and
        // PipelineValidator (produces an ID_BLANK ValidationError).
        if (tenantIds == null) tenantIds = List.of();
        else tenantIds = List.copyOf(tenantIds);
        if (trigger == null) trigger = new TriggerDefinition(TriggerType.MANUAL, null, null, null);
        if (errorStrategy == null) errorStrategy = ErrorStrategy.STOP;
        if (maxRetries < 0) maxRetries = 3;
        if (stages == null) stages = List.of();
        else stages = List.copyOf(stages);
        if (output == null) output = new OutputDefinition(OutputType.NONE, null, null, null);
        if (security == null) security = PipelineSecurityProperties.DEFAULT;
    }

    /**
     * Backward-compatible 12-arg constructor — omits {@code resultTemplate},
     * which defaults to {@code null}. Retained so existing YAML mapper
     * fixtures and external callers compile unchanged.
     */
    public PipelineDefinition(String id, String name, String description,
                              List<String> tenantIds, boolean enabled,
                              TriggerDefinition trigger, ErrorStrategy errorStrategy,
                              int maxRetries, String deadLetterUri,
                              List<StageDefinition> stages, OutputDefinition output,
                              PipelineSecurityProperties security) {
        this(id, name, description, tenantIds, enabled, trigger, errorStrategy,
                maxRetries, deadLetterUri, stages, output, security, null);
    }
}
