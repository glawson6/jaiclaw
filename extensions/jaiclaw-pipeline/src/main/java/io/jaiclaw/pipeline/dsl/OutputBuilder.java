package io.jaiclaw.pipeline.dsl;

import io.jaiclaw.pipeline.OutputDefinition;
import io.jaiclaw.pipeline.OutputType;

/**
 * Fluent builder for {@link OutputDefinition}. Chains back to the parent {@link PipelineBuilder}.
 */
public class OutputBuilder {

    private final PipelineBuilder parent;
    private OutputType type = OutputType.NONE;
    private String channelId;
    private String uri;
    private String template;

    OutputBuilder(PipelineBuilder parent) {
        this.parent = parent;
    }

    /**
     * Send output to a JaiClaw channel.
     *
     * @param channelId the channel identifier
     * @return this builder for further configuration
     */
    public OutputBuilder channel(String channelId) {
        this.type = OutputType.CHANNEL;
        this.channelId = channelId;
        return this;
    }

    /**
     * Send output to an arbitrary Camel endpoint.
     *
     * @param uri the Camel URI
     * @return this builder for further configuration
     */
    public OutputBuilder camelUri(String uri) {
        this.type = OutputType.CAMEL_URI;
        this.uri = uri;
        return this;
    }

    /**
     * Log the output.
     *
     * @return this builder for further configuration
     */
    public OutputBuilder log() {
        this.type = OutputType.LOG;
        return this;
    }

    /**
     * Discard the output.
     *
     * @return this builder for further configuration
     */
    public OutputBuilder none() {
        this.type = OutputType.NONE;
        return this;
    }

    /**
     * Set the output template. Supports {@code {{stages.X.output}}} placeholders.
     *
     * @param template the template string
     * @return this builder for further configuration
     */
    public OutputBuilder template(String template) {
        this.template = template;
        return this;
    }

    OutputDefinition build() {
        return new OutputDefinition(type, channelId, uri, template);
    }
}
