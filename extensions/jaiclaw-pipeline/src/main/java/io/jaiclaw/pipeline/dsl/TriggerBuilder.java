package io.jaiclaw.pipeline.dsl;

import io.jaiclaw.pipeline.TriggerDefinition;
import io.jaiclaw.pipeline.TriggerType;

/**
 * Fluent builder for {@link TriggerDefinition}. Chains back to the parent {@link PipelineBuilder}.
 */
public class TriggerBuilder {

    private final PipelineBuilder parent;
    private TriggerType type = TriggerType.MANUAL;
    private String uri;
    private String expression;
    private String path;

    TriggerBuilder(PipelineBuilder parent) {
        this.parent = parent;
    }

    /**
     * File-based trigger.
     *
     * @param uri the file URI (e.g., "file://inbox")
     * @return the parent pipeline builder
     */
    public PipelineBuilder file(String uri) {
        this.type = TriggerType.FILE;
        this.uri = uri;
        return parent;
    }

    /**
     * Cron-based trigger.
     *
     * @param expression the cron expression
     * @return the parent pipeline builder
     */
    public PipelineBuilder cron(String expression) {
        this.type = TriggerType.CRON;
        this.expression = expression;
        return parent;
    }

    /**
     * HTTP endpoint trigger.
     *
     * @param path the HTTP path
     * @return the parent pipeline builder
     */
    public PipelineBuilder http(String path) {
        this.type = TriggerType.HTTP;
        this.path = path;
        return parent;
    }

    /**
     * Arbitrary Camel URI trigger.
     *
     * @param uri the Camel URI
     * @return the parent pipeline builder
     */
    public PipelineBuilder camelUri(String uri) {
        this.type = TriggerType.CAMEL_URI;
        this.uri = uri;
        return parent;
    }

    /**
     * Manual (programmatic) trigger only.
     *
     * @return the parent pipeline builder
     */
    public PipelineBuilder manual() {
        this.type = TriggerType.MANUAL;
        return parent;
    }

    TriggerDefinition build() {
        return new TriggerDefinition(type, uri, expression, path);
    }
}
