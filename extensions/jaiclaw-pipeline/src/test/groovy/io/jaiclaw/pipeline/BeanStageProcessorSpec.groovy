package io.jaiclaw.pipeline

import org.apache.camel.Exchange
import org.apache.camel.Message
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.util.function.Function

class BeanStageProcessorSpec extends Specification {

    ApplicationContext applicationContext = Mock()
    BeanStageProcessor processor = new BeanStageProcessor(applicationContext)

    def "invokes bean and sets result as body"() {
        given:
        Function<String, String> bean = { input -> "processed: " + input } as Function
        applicationContext.getBean("myProcessor") >> bean

        Exchange exchange = Mock()
        Message message = Mock()
        exchange.getIn() >> message
        message.getBody(String.class) >> "input text"

        StageDefinition stage = new StageDefinition(
                "process", StageType.PROCESSOR, "myProcessor",
                null, null, null, null, null, null
        )
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null, 0, 1, null, null, null, null
        )

        when:
        processor.process(exchange, stage, ctx)

        then:
        1 * message.setBody("processed: input text")
    }

    def "throws when bean name is blank"() {
        given:
        Exchange exchange = Mock()
        Message message = Mock()
        exchange.getIn() >> message

        StageDefinition stage = new StageDefinition(
                "process", StageType.PROCESSOR, "",
                null, null, null, null, null, null
        )
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null, 0, 1, null, null, null, null
        )

        when:
        processor.process(exchange, stage, ctx)

        then:
        thrown(IllegalArgumentException)
    }

    def "throws when bean does not implement Function"() {
        given:
        applicationContext.getBean("notAFunction") >> "just a string"

        Exchange exchange = Mock()
        Message message = Mock()
        exchange.getIn() >> message

        StageDefinition stage = new StageDefinition(
                "process", StageType.PROCESSOR, "notAFunction",
                null, null, null, null, null, null
        )
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null, 0, 1, null, null, null, null
        )

        when:
        processor.process(exchange, stage, ctx)

        then:
        thrown(IllegalArgumentException)
    }
}
