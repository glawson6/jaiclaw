package io.jaiclaw.pipeline;

import io.jaiclaw.camel.PipelineEnvelope;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable context evolving {@link PipelineEnvelope} with richer stage tracking.
 * Carries execution state through pipeline stages, accumulating outputs and metadata.
 *
 * @param pipelineId    unique pipeline definition identifier
 * @param executionId   unique execution instance identifier (UUID)
 * @param tenantId      tenant that owns this execution (nullable for single-tenant)
 * @param correlationId correlation ID for distributed tracing
 * @param stageIndex    current stage index (0-based)
 * @param totalStages   total number of stages in the pipeline
 * @param replyChannelId channel to send the final result to (nullable)
 * @param replyPeerId   peer to reply to (nullable)
 * @param stageOutputs  accumulated outputs from completed stages (keyed by stage name)
 * @param metadata      additional metadata for the execution
 */
public record PipelineContext(
        String pipelineId,
        String executionId,
        String tenantId,
        String correlationId,
        int stageIndex,
        int totalStages,
        String replyChannelId,
        String replyPeerId,
        Map<String, StageOutput> stageOutputs,
        Map<String, String> metadata
) {
    /**
     * Output from a completed stage.
     *
     * @param output      the stage's output text
     * @param metadata    additional metadata from the stage
     * @param completedAt when the stage completed
     */
    public record StageOutput(
            String output,
            Map<String, String> metadata,
            Instant completedAt
    ) {
        public StageOutput {
            if (output == null) output = "";
            if (metadata == null) metadata = Map.of();
            else metadata = Map.copyOf(metadata);
            if (completedAt == null) completedAt = Instant.now();
        }
    }

    public PipelineContext {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        if (executionId == null || executionId.isBlank()) {
            executionId = UUID.randomUUID().toString();
        }
        if (stageOutputs == null) {
            stageOutputs = Map.of();
        } else {
            stageOutputs = Map.copyOf(stageOutputs);
        }
        if (metadata == null) {
            metadata = Map.of();
        } else {
            metadata = Map.copyOf(metadata);
        }
    }

    /**
     * Advance to the next stage, recording the current stage's output.
     *
     * @param stageName     name of the completed stage
     * @param output        output text from the completed stage
     * @return a new context with incremented stageIndex and appended output
     */
    public PipelineContext nextStage(String stageName, String output) {
        Map<String, StageOutput> newOutputs = new LinkedHashMap<>(stageOutputs);
        newOutputs.put(stageName, new StageOutput(output, Map.of(), Instant.now()));
        return new PipelineContext(
                pipelineId, executionId, tenantId, correlationId,
                stageIndex + 1, totalStages,
                replyChannelId, replyPeerId,
                newOutputs, metadata
        );
    }

    /**
     * Whether this is the last stage in the pipeline.
     */
    public boolean isLastStage() {
        return stageIndex >= totalStages - 1;
    }

    /**
     * Get the current stage name from a list of stage definitions.
     *
     * @param stages the pipeline's stage definitions
     * @return the name of the current stage, or "unknown" if out of bounds
     */
    public String currentStageName(List<StageDefinition> stages) {
        if (stageIndex >= 0 && stageIndex < stages.size()) {
            return stages.get(stageIndex).name();
        }
        return "unknown";
    }

    /**
     * Metadata key under which {@link PipelineRouteBuilder} stores the original
     * trigger payload so {@code {{input}}} placeholders resolve through every
     * stage hop. Truncated to 32 KB to bound memory usage.
     */
    public static final String INPUT_METADATA_KEY = "__input__";

    /** Maximum number of trigger-payload bytes retained in metadata. */
    public static final int MAX_INPUT_BYTES = 32 * 1024;

    /**
     * Return all variables resolvable from this context, keyed by the dotted
     * placeholder name a template would use (e.g. {@code "pipeline.id"},
     * {@code "stages.research.output"}, {@code "input"}).
     *
     * <p>Used by {@link TemplateResolver} to format the warn-on-miss message
     * and by the validator's introspection paths.
     */
    public Map<String, String> availableVariables() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("pipeline.id", pipelineId == null ? "" : pipelineId);
        result.put("pipeline.executionId", executionId == null ? "" : executionId);
        result.put("pipeline.tenantId", tenantId == null ? "" : tenantId);
        result.put("pipeline.correlationId", correlationId == null ? "" : correlationId);
        String input = metadata == null ? null : metadata.get(INPUT_METADATA_KEY);
        if (input != null) {
            result.put("input", input);
        }
        if (stageOutputs != null) {
            for (Map.Entry<String, StageOutput> entry : stageOutputs.entrySet()) {
                String stage = entry.getKey();
                StageOutput out = entry.getValue();
                if (out == null) continue;
                result.put("stages." + stage + ".output", out.output() == null ? "" : out.output());
                if (out.metadata() != null) {
                    for (Map.Entry<String, String> m : out.metadata().entrySet()) {
                        result.put("stages." + stage + ".metadata." + m.getKey(), m.getValue() == null ? "" : m.getValue());
                    }
                }
            }
        }
        return result;
    }

    /**
     * Bridge factory: create a PipelineContext from an existing {@link PipelineEnvelope}.
     *
     * @param envelope the legacy pipeline envelope
     * @return a new PipelineContext with data mapped from the envelope
     */
    public static PipelineContext fromEnvelope(PipelineEnvelope envelope) {
        Map<String, StageOutput> outputs = new LinkedHashMap<>();
        List<String> envelopeOutputs = envelope.stageOutputs();
        for (int i = 0; i < envelopeOutputs.size(); i++) {
            outputs.put("stage-" + i, new StageOutput(envelopeOutputs.get(i), Map.of(), Instant.now()));
        }
        return new PipelineContext(
                envelope.pipelineId(),
                UUID.randomUUID().toString(),
                null,
                envelope.correlationId(),
                envelope.stageIndex(),
                envelope.totalStages(),
                envelope.replyChannelId(),
                envelope.replyPeerId(),
                outputs,
                Map.of()
        );
    }
}
