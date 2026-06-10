package io.jaiclaw.pipeline

import spock.lang.Specification

class PipelineRouteBuilderCronSpec extends Specification {

    private static PipelineRouteBuilder builderFor(String pipelineId, TriggerDefinition trigger) {
        PipelineDefinition definition = new PipelineDefinition(
                pipelineId, null, null, List.of(), true, trigger,
                ErrorStrategy.STOP, 0, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)],
                null, null)
        return new PipelineRouteBuilder(definition, PipelineProperties.PipelineDefaults.DEFAULT,
                null, null, null, null, null, null, null, null, null)
    }

    def "CRON trigger generates a quartz:// URI with URL-encoded cron expression"() {
        given:
        TriggerDefinition trigger = new TriggerDefinition(TriggerType.CRON, null, "0 0/15 * * * ?", null)
        PipelineRouteBuilder builder = builderFor("my-pipe", trigger)

        when:
        String uri = builder.resolveTriggerUri("my-pipe")

        then:
        uri.startsWith("quartz://jaiclaw-pipelines/my-pipe?cron=")
        // Spaces and '?' must be encoded.
        !uri.contains(" ")
        uri.contains("0+0%2F15+*+*+*+%3F") || uri.contains("0%200%2F15%20*%20*%20*%20%3F")
    }

    def "CRON trigger with weekday spec encodes the entire expression"() {
        given:
        TriggerDefinition trigger = new TriggerDefinition(TriggerType.CRON, null, "0 0 7 ? * MON-FRI", null)
        PipelineRouteBuilder builder = builderFor("daily", trigger)

        when:
        String uri = builder.resolveTriggerUri("daily")

        then:
        uri.startsWith("quartz://jaiclaw-pipelines/daily?cron=")
        uri.contains("MON-FRI")
        !uri.contains(" ")
    }

    def "CRON trigger with blank expression throws helpful error"() {
        given:
        TriggerDefinition trigger = new TriggerDefinition(TriggerType.CRON, null, expr, null)
        PipelineRouteBuilder builder = builderFor("oops", trigger)

        when:
        builder.resolveTriggerUri("oops")

        then:
        IllegalStateException ex = thrown()
        ex.message.contains("oops")
        ex.message.contains("CRON")

        where:
        expr << [null, "", "   "]
    }

    def "non-CRON triggers are unaffected by the CRON fix"() {
        expect:
        builderFor("p", new TriggerDefinition(TriggerType.MANUAL, null, null, null))
                .resolveTriggerUri("p") == "direct:pipeline-p"
        builderFor("p", new TriggerDefinition(TriggerType.HTTP, null, null, "/run"))
                .resolveTriggerUri("p") == "direct:pipeline-p-http"
        builderFor("p", new TriggerDefinition(TriggerType.FILE, "file://inbox", null, null))
                .resolveTriggerUri("p") == "file://inbox"
        builderFor("p", new TriggerDefinition(TriggerType.CAMEL_URI, "direct:custom", null, null))
                .resolveTriggerUri("p") == "direct:custom"
    }
}
