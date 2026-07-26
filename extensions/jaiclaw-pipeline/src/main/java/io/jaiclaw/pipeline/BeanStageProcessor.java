package io.jaiclaw.pipeline;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.function.Function;

/**
 * Stage processor for PROCESSOR-type stages. Looks up a Spring bean by
 * name and dispatches based on its shape:
 *
 * <ul>
 *   <li>If the bean implements {@link ConfigurableStageProcessor}, its
 *       {@code process(exchange, stage, context, config)} is invoked
 *       with the stage's config map — this is the SPI Pipeline Studio
 *       drives from the palette.</li>
 *   <li>Otherwise, the bean must implement {@code Function<String,String>};
 *       {@code apply(input)} is invoked with the exchange body and the
 *       return value becomes the new body. This is the legacy path —
 *       still supported for hand-rolled processor beans.</li>
 * </ul>
 *
 * <p>Bean-lookup + dispatch logic lives here (not in
 * {@link PipelineRouteBuilder}) so the switch in
 * {@code resolveProcessor(StageType)} stays trivial and the two
 * PROCESSOR-shape paths share every hook / audit / metric side
 * effect above them in the route builder.
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

        if (bean instanceof ConfigurableStageProcessor configurable) {
            log.debug("Processor stage '{}' invoking ConfigurableStageProcessor bean '{}' with {} config keys",
                    stage.name(), beanName, stage.config().size());
            configurable.process(exchange, stage, context, stage.config());
            return;
        }

        if (!(bean instanceof Function)) {
            throw new IllegalArgumentException(
                    "Bean '" + beanName + "' must implement either ConfigurableStageProcessor or Function<String, String>");
        }

        Function<String, String> processor = (Function<String, String>) bean;
        String input = exchange.getIn().getBody(String.class);

        log.debug("Processor stage '{}' invoking Function bean '{}'", stage.name(), beanName);

        String result = processor.apply(input);
        exchange.getIn().setBody(result);
    }
}
