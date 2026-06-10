package io.jaiclaw.pipeline.dsl;

import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.StageType;
import io.jaiclaw.pipeline.TransportAuthType;

import java.time.Duration;

/**
 * Fluent builder for {@link StageDefinition}. Chains back to the parent {@link PipelineBuilder}
 * for adding more stages or configuring output.
 */
public class StageBuilder {

    private final PipelineBuilder parent;
    private final String name;
    private StageType type;
    private String bean;
    private String agentId;
    private String systemPrompt;
    private String channelId;
    private String uri;
    private Duration timeout;
    private String transportUri;
    private TransportAuthType transportAuthType;
    private String transportSecret;
    private String transportHeaderName;

    StageBuilder(PipelineBuilder parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    /**
     * Configure this stage as an agent stage.
     *
     * @param agentId the agent identifier
     * @return this builder for further configuration
     */
    public StageBuilder agent(String agentId) {
        this.type = StageType.AGENT;
        this.agentId = agentId;
        return this;
    }

    /**
     * Configure this stage as a processor stage (Spring bean).
     *
     * @param beanName the Spring bean name (must implement {@code Function<String, String>})
     * @return this builder for further configuration
     */
    public StageBuilder processor(String beanName) {
        this.type = StageType.PROCESSOR;
        this.bean = beanName;
        return this;
    }

    /**
     * Configure this stage as a Camel endpoint stage.
     *
     * @param uri the Camel endpoint URI
     * @return this builder for further configuration
     */
    public StageBuilder camel(String uri) {
        this.type = StageType.CAMEL;
        this.uri = uri;
        return this;
    }

    /**
     * Set the system prompt template for agent stages.
     *
     * @param systemPrompt the prompt (supports {@code {{stages.X.output}}} placeholders)
     * @return this builder
     */
    public StageBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    /**
     * Set the channel ID for agent stages.
     *
     * @param channelId the channel identifier
     * @return this builder
     */
    public StageBuilder channelId(String channelId) {
        this.channelId = channelId;
        return this;
    }

    /**
     * Set the stage execution timeout.
     *
     * @param timeout the timeout duration
     * @return this builder
     */
    public StageBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Set the inter-stage transport URI without authentication.
     *
     * @param uri the transport URI (e.g., "kafka:my-topic")
     * @return this builder
     */
    public StageBuilder transport(String uri) {
        this.transportUri = uri;
        return this;
    }

    /**
     * Set the inter-stage transport URI with authentication.
     *
     * @param uri      the transport URI
     * @param authType the authentication method
     * @param secret   the shared secret
     * @return this builder
     */
    public StageBuilder transport(String uri, TransportAuthType authType, String secret) {
        this.transportUri = uri;
        this.transportAuthType = authType;
        this.transportSecret = secret;
        return this;
    }

    /**
     * Set the inter-stage transport URI with authentication and custom header name.
     *
     * @param uri        the transport URI
     * @param authType   the authentication method
     * @param secret     the shared secret
     * @param headerName the header name carrying the token/signature
     * @return this builder
     */
    public StageBuilder transport(String uri, TransportAuthType authType, String secret, String headerName) {
        this.transportUri = uri;
        this.transportAuthType = authType;
        this.transportSecret = secret;
        this.transportHeaderName = headerName;
        return this;
    }

    /**
     * Start defining the next stage (delegates to parent).
     *
     * @param stageName the name of the next stage
     * @return a new stage builder
     */
    public StageBuilder stage(String stageName) {
        return parent.stage(stageName);
    }

    /**
     * Readability alias for {@link #stage(String)}. Lets the DSL read like
     * {@code .stage("a").agent("x").then("b").agent("y")}.
     */
    public StageBuilder then(String stageName) {
        return parent.stage(stageName);
    }

    /**
     * Start defining the output (delegates to parent).
     *
     * @return the output builder
     */
    public OutputBuilder output() {
        return parent.output();
    }

    StageDefinition build() {
        StageDefinition.TransportConfig transport = null;
        if (transportUri != null) {
            StageDefinition.TransportAuth auth = null;
            if (transportAuthType != null && transportAuthType != TransportAuthType.NONE) {
                auth = new StageDefinition.TransportAuth(transportAuthType, transportSecret, transportHeaderName);
            }
            transport = new StageDefinition.TransportConfig(transportUri, auth);
        }
        return new StageDefinition(name, type, bean, agentId, systemPrompt, channelId, uri, timeout, transport);
    }
}
