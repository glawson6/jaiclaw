package io.jaiclaw.compliance.gdpr

import io.jaiclaw.config.ModelsProperties
import io.jaiclaw.core.gdpr.ProcessorMetadataExporter
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DefaultProcessorMetadataExporterSpec extends Specification {

    Clock fixed = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC)

    def "exports sub-processor entry per configured LLM provider with BAA eligibility flag"() {
        given:
        def props = new ModelsProperties([
                "bedrock":   new ModelsProperties.ModelProviderConfig("https://bedrock.us-east-1.amazonaws.com", "k", "aws", null, null, null, null),
                "anthropic": new ModelsProperties.ModelProviderConfig("https://api.anthropic.com", "k", "sdk", null, null, null, null),
        ])
        def exporter = new DefaultProcessorMetadataExporter(
                "JaiClaw gateway prod", "compliance@example.com", props, [], fixed)

        when:
        ProcessorMetadataExporter.ProcessorMetadata md = exporter.exportForTenant("acme")

        then:
        md.processorName() == "JaiClaw gateway prod"
        md.generatedAt() == Instant.parse("2026-07-07T12:00:00Z")
        md.subProcessors().size() == 2
        ProcessorMetadataExporter.SubProcessor bedrock = md.subProcessors().find { it.name() == "bedrock" }
        bedrock.baaEligible()
        bedrock.location() == "https://bedrock.us-east-1.amazonaws.com"
        ProcessorMetadataExporter.SubProcessor anthropic = md.subProcessors().find { it.name() == "anthropic" }
        !anthropic.baaEligible()
    }

    def "security measures fall back to a sensible baseline when none supplied"() {
        given:
        def props = new ModelsProperties([:])
        def exporter = new DefaultProcessorMetadataExporter(
                "JaiClaw prod", "c@e.com", props, [], fixed)

        when:
        ProcessorMetadataExporter.ProcessorMetadata md = exporter.exportForTenant("acme")

        then:
        !md.securityMeasures().isEmpty()
        md.securityMeasures().any { it.contains("Multi-tenant") }
    }

    def "blank tenantId is rejected"() {
        given:
        def exporter = new DefaultProcessorMetadataExporter(
                "JaiClaw", "c@e.com", new ModelsProperties([:]), [], fixed)

        when:
        exporter.exportForTenant(t)

        then:
        thrown(IllegalArgumentException)

        where:
        t << [null, ""]
    }
}
