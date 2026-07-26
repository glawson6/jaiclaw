package io.jaiclaw.pipeline.authoring

import io.jaiclaw.pipeline.ConfigurableStageProcessor
import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.PipelineProcessor
import io.jaiclaw.pipeline.StageDefinition
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.util.function.Function

class PipelineCatalogServiceSpec extends Specification {

    ApplicationContext ctx = Mock()
    ObjectProvider<?> channelRegistryProvider = Mock()

    PipelineCatalogService catalog = new PipelineCatalogService(ctx, channelRegistryProvider)

    def "catalog exposes engine enum names"() {
        given:
        ctx.getBeanNamesForAnnotation(PipelineProcessor.class) >> ([] as String[])
        ctx.getBeanNamesForType(Function.class) >> ([] as String[])
        channelRegistryProvider.getIfAvailable() >> null

        when:
        Map<String, Object> tree = catalog.catalog()

        then:
        tree.triggerTypes.containsAll(["MANUAL", "HTTP", "CRON", "FILE", "CAMEL_URI"])
        tree.stageTypes == ["AGENT", "PROCESSOR", "CAMEL"]
        tree.outputTypes.containsAll(["CHANNEL", "CAMEL_URI", "LOG", "NONE"])
        tree.errorStrategies.containsAll(["STOP", "RETRY_THEN_FAIL", "DEAD_LETTER"])
    }

    def "@PipelineProcessor beans surface with their metadata + schema"() {
        given:
        String[] beanNames = ["upperProcessor"] as String[]
        ctx.getBeanNamesForAnnotation(PipelineProcessor.class) >> beanNames
        ctx.getType("upperProcessor") >> UpperProcessor.class
        ctx.findAnnotationOnBean("upperProcessor", PipelineProcessor.class) >>
                UpperProcessor.class.getAnnotation(PipelineProcessor.class)
        ctx.getBean("upperProcessor") >> new UpperProcessor()
        ctx.getBeanNamesForType(Function.class) >> ([] as String[])
        channelRegistryProvider.getIfAvailable() >> null

        when:
        Map<String, Object> tree = catalog.catalog()

        then:
        List<Map<String, Object>> processors = tree.processors as List
        processors.size() == 1
        processors[0].name == "Uppercase"
        processors[0].category == "Transform"
        processors[0].configSchema.contains("locale")
    }

    def "bare Function<String,String> beans surface as customBeans"() {
        given:
        ctx.getBeanNamesForAnnotation(PipelineProcessor.class) >> ([] as String[])
        ctx.getBeanNamesForType(Function.class) >> (["exclaimer"] as String[])
        ctx.getType("exclaimer") >> ExclaimFunction.class
        channelRegistryProvider.getIfAvailable() >> null

        when:
        Map<String, Object> tree = catalog.catalog()

        then:
        tree.customBeans == ["exclaimer"]
    }

    // Fixture: @PipelineProcessor-annotated ConfigurableStageProcessor.
    // Groovy dispatches to the SAM method dynamically — the parameter
    // type org.apache.camel.Exchange resolves at bytecode-load time
    // without needing an import at Groovy compile time.
    @PipelineProcessor(name = "Uppercase", category = "Transform",
            description = "Uppercases the input", icon = "case")
    static class UpperProcessor implements ConfigurableStageProcessor {
        @Override
        void process(org.apache.camel.Exchange exchange, StageDefinition stage,
                     PipelineContext context, Map<String, String> config) {
            // No-op body — catalog test never invokes process().
        }

        @Override
        String configSchema() {
            return '{"type":"object","properties":{"locale":{"type":"string"}}}'
        }
    }

    static class ExclaimFunction implements Function<String, String> {
        @Override
        String apply(String s) { return s + "!" }
    }
}
