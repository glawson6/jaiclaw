package io.jaiclaw.pipeline;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage processor that sends the exchange to an arbitrary Camel endpoint URI.
 */
public class CamelStageProcessor implements StageProcessor {

    private static final Logger log = LoggerFactory.getLogger(CamelStageProcessor.class);

    private final ProducerTemplate producerTemplate;

    public CamelStageProcessor(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @Override
    public void process(Exchange exchange, StageDefinition stage, PipelineContext context) throws Exception {
        String uri = stage.uri();
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException(
                    "Stage '" + stage.name() + "' has type CAMEL but no URI configured");
        }

        log.debug("Camel stage '{}' sending to URI '{}'", stage.name(), uri);

        Exchange result = producerTemplate.send(uri, exchange);

        // Propagate result body back if the endpoint returned one
        if (result.getMessage().getBody() != null) {
            exchange.getIn().setBody(result.getMessage().getBody(String.class));
        }

        // Propagate exceptions
        if (result.getException() != null) {
            throw result.getException();
        }
    }
}
