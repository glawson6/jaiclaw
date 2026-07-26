package io.jaiclaw.pipeline;

import org.apache.camel.Exchange;

import java.util.Map;

/**
 * Stage-processor SPI for beans that accept per-stage configuration.
 *
 * <p>Where {@link BeanStageProcessor} invokes a bare
 * {@code Function<String, String>} bean (no metadata, no config), a
 * {@code ConfigurableStageProcessor} implementation:
 *
 * <ul>
 *   <li>declares its metadata via {@link PipelineProcessor} on the
 *       implementing class so the Pipeline Studio catalog + palette
 *       can render it,</li>
 *   <li>receives per-stage configuration in {@link StageDefinition#config()}
 *       — a {@code Map<String, String>} the Studio inspector populates
 *       via the schema returned by {@link #configSchema()},</li>
 *   <li>is dispatched by {@code PipelineRouteBuilder} at the same
 *       point in the stage lifecycle as {@link BeanStageProcessor}
 *       (after transport auth + tenant context + input validation).</li>
 * </ul>
 *
 * <p>Implementations should be side-effect free with respect to
 * shared state — the runtime may invoke the same instance from
 * multiple stages concurrently.
 *
 * @see PipelineProcessor
 * @see BeanStageProcessor
 */
public interface ConfigurableStageProcessor {

    /**
     * Process one exchange. Signature intentionally matches
     * {@code StageProcessor.process(...)} with an extra {@code config}
     * argument threaded from {@link StageDefinition#config()}.
     *
     * @param exchange the Camel exchange; body is the stage input,
     *                 typically {@code String}. Implementations set
     *                 the body to the stage output.
     * @param stage    the stage definition (name, config, timeout, etc.)
     * @param context  the current {@link PipelineContext}
     * @param config   the stage's configuration map (never {@code null};
     *                 empty if no configuration was set)
     */
    void process(Exchange exchange,
                 StageDefinition stage,
                 PipelineContext context,
                 Map<String, String> config) throws Exception;

    /**
     * A JSON Schema describing this processor's configuration surface.
     * The Studio SPA feeds this schema to react-jsonschema-form to
     * render the inspector panel. Default returns the empty-object
     * schema ("no configuration").
     */
    default String configSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
    }
}
