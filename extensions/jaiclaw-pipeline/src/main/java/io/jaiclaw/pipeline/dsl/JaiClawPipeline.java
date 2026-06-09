package io.jaiclaw.pipeline.dsl;

import io.jaiclaw.pipeline.PipelineDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for defining pipelines via Java code.
 * Analogous to Camel's {@code RouteBuilder} — users extend this class
 * and override {@link #define()} to build pipelines using the fluent API.
 *
 * <p>Can define multiple pipelines in a single class by calling
 * {@link #pipeline(String)} multiple times.
 *
 * <pre>
 * {@literal @}Configuration
 * public class MyPipelines extends JaiClawPipeline {
 *     {@literal @}Override
 *     public void define() {
 *         pipeline("content-pipeline")
 *             .name("Content Creation Pipeline")
 *             .trigger().http("/api/content/create")
 *             .stage("research").agent("researcher")
 *                 .systemPrompt("Research the given topic.")
 *             .stage("write").agent("writer")
 *                 .systemPrompt("Write using: {{stages.research.output}}")
 *             .output().channel("slack");
 *     }
 * }
 * </pre>
 */
public abstract class JaiClawPipeline {

    private final List<PipelineBuilder> builders = new ArrayList<>();

    /**
     * Override to define pipelines using the fluent API.
     */
    public abstract void define();

    /**
     * Start defining a new pipeline with the given ID.
     *
     * @param id unique pipeline identifier
     * @return a fluent builder for configuring the pipeline
     */
    protected PipelineBuilder pipeline(String id) {
        PipelineBuilder builder = new PipelineBuilder(id);
        builders.add(builder);
        return builder;
    }

    /**
     * Called by auto-config to collect built pipeline definitions.
     * Invokes {@link #define()} and then builds all definitions.
     *
     * @return the list of pipeline definitions
     */
    public List<PipelineDefinition> getDefinitions() {
        define();
        return builders.stream()
                .map(PipelineBuilder::build)
                .toList();
    }
}
