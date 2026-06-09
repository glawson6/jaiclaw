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
        PipelineSecurityProperties security
) {
    public PipelineDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Pipeline id must not be blank");
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
}
