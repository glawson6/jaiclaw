package io.jaiclaw.example.camel.pdffiller;

import io.jaiclaw.camel.CamelMessageConverter;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Inbound route: watches the configured inbox directory for JSON files and
 * forwards them to the SEDA inbound queue for agent processing.
 *
 * <p>Each JSON file's content is sent to the agent along with PDF form field
 * descriptions (injected by the system prompt). The agent maps JSON keys to
 * PDF form fields.
 */
@Configuration
public class JsonIngestRoute extends RouteBuilder {

    private final String inbox;
    private final TemplateManager templateManager;

    public JsonIngestRoute(
            @Value("${app.inbox:target/data/inbox}") String inbox,
            TemplateManager templateManager) {
        this.inbox = inbox;
        this.templateManager = templateManager;
    }

    @Override
    public void configure() {
        fromF("file:%s?include=.*\\.json&move=.done/${file:name}&readLock=changed", inbox)
                .routeId("json-file-ingest")
                .setHeader(CamelMessageConverter.HEADER_PEER_ID,
                        simple("${file:name.noext}"))
                .process(exchange -> {
                    String jsonBody = exchange.getIn().getBody(String.class);
                    String enriched = "AVAILABLE PDF FORM FIELDS (in document order):\n"
                            + templateManager.getFieldDescriptions()
                            + "\nJSON DATA TO MAP:\n" + jsonBody
                            + "\n\nMap each value from the JSON to the correct PDF field name above.";
                    exchange.getIn().setBody(enriched);
                })
                .to("seda:jaiclaw-pdf-filler-in");
    }
}
