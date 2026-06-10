package io.jaiclaw.pipeline.dsl;

import io.jaiclaw.pipeline.ErrorStrategy;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineSecurityProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fluent builder for {@link PipelineDefinition}. Entry point of the code DSL.
 */
public class PipelineBuilder {

    private final String id;
    private String name;
    private String description;
    private final List<String> tenantIds = new ArrayList<>();
    private boolean enabled = true;
    private ErrorStrategy errorStrategy = ErrorStrategy.STOP;
    private int maxRetries = 3;
    private String deadLetterUri;
    private TriggerBuilder triggerBuilder;
    private final List<StageBuilder> stageBuilders = new ArrayList<>();
    private OutputBuilder outputBuilder;
    private SecurityBuilder securityBuilder;

    PipelineBuilder(String id) {
        this.id = id;
    }

    /**
     * Set the pipeline display name.
     */
    public PipelineBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the pipeline description.
     */
    public PipelineBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the tenant IDs that may execute this pipeline.
     */
    public PipelineBuilder tenantIds(String... tenantIds) {
        this.tenantIds.addAll(Arrays.asList(tenantIds));
        return this;
    }

    /**
     * Set whether the pipeline is enabled.
     */
    public PipelineBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Set the error handling strategy.
     */
    public PipelineBuilder errorStrategy(ErrorStrategy errorStrategy) {
        this.errorStrategy = errorStrategy;
        return this;
    }

    /**
     * Set the maximum number of retries for RETRY_THEN_FAIL strategy.
     */
    public PipelineBuilder maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Set the dead-letter queue URI for DEAD_LETTER strategy.
     */
    public PipelineBuilder deadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
        return this;
    }

    /**
     * Start configuring the pipeline trigger.
     *
     * @return a trigger builder
     */
    public TriggerBuilder trigger() {
        this.triggerBuilder = new TriggerBuilder(this);
        return triggerBuilder;
    }

    /**
     * Start configuring a new pipeline stage.
     *
     * @param name unique stage name
     * @return a stage builder
     */
    public StageBuilder stage(String name) {
        StageBuilder builder = new StageBuilder(this, name);
        stageBuilders.add(builder);
        return builder;
    }

    /**
     * Readability alias for {@link #stage(String)}. Lets multi-stage pipelines
     * read top-to-bottom: {@code .trigger().http("/x").then("a")...then("b")}.
     */
    public StageBuilder then(String name) {
        return stage(name);
    }

    /**
     * Start configuring the pipeline output.
     *
     * @return an output builder
     */
    public OutputBuilder output() {
        this.outputBuilder = new OutputBuilder(this);
        return outputBuilder;
    }

    /**
     * Start configuring pipeline security.
     *
     * @return a security builder
     */
    public SecurityBuilder security() {
        this.securityBuilder = new SecurityBuilder(this);
        return securityBuilder;
    }

    /**
     * Build the pipeline definition. Validates required fields and uniqueness constraints.
     *
     * @return the built pipeline definition
     * @throws IllegalStateException if validation fails
     */
    PipelineDefinition build() {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Pipeline ID must not be blank");
        }
        if (stageBuilders.isEmpty()) {
            throw new IllegalStateException("Pipeline '" + id + "' must have at least one stage");
        }

        // Validate unique stage names
        Set<String> stageNames = new HashSet<>();
        for (StageBuilder sb : stageBuilders) {
            StageBuilder check = sb;
            // Build the stage to get the name from StageDefinition validation
        }

        List<io.jaiclaw.pipeline.StageDefinition> stages = stageBuilders.stream()
                .map(StageBuilder::build)
                .toList();

        for (io.jaiclaw.pipeline.StageDefinition stage : stages) {
            if (!stageNames.add(stage.name())) {
                throw new IllegalStateException(
                        "Pipeline '" + id + "' has duplicate stage name: '" + stage.name() + "'");
            }
        }

        PipelineSecurityProperties security = securityBuilder != null
                ? securityBuilder.build()
                : PipelineSecurityProperties.DEFAULT;

        return new PipelineDefinition(
                id, name, description, tenantIds, enabled,
                triggerBuilder != null ? triggerBuilder.build() : null,
                errorStrategy, maxRetries, deadLetterUri,
                stages,
                outputBuilder != null ? outputBuilder.build() : null,
                security
        );
    }
}
