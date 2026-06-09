package io.jaiclaw.pipeline;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.function.Function;

/**
 * Stage processor that looks up a Spring bean by name and invokes it.
 * The bean must implement {@code Function<String, String>} (input → output).
 */
public class BeanStageProcessor implements StageProcessor {

    private static final Logger log = LoggerFactory.getLogger(BeanStageProcessor.class);

    private final ApplicationContext applicationContext;

    public BeanStageProcessor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange, StageDefinition stage, PipelineContext context) throws Exception {
        String beanName = stage.bean();
        if (beanName == null || beanName.isBlank()) {
            throw new IllegalArgumentException(
                    "Stage '" + stage.name() + "' has type PROCESSOR but no bean name configured");
        }

        Object bean = applicationContext.getBean(beanName);
        if (!(bean instanceof Function)) {
            throw new IllegalArgumentException(
                    "Bean '" + beanName + "' does not implement Function<String, String>");
        }

        Function<String, String> processor = (Function<String, String>) bean;
        String input = exchange.getIn().getBody(String.class);

        log.debug("Processor stage '{}' invoking bean '{}'", stage.name(), beanName);

        String result = processor.apply(input);
        exchange.getIn().setBody(result);
    }
}
