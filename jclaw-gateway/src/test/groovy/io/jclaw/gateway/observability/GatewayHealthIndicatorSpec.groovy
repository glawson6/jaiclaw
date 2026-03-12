package io.jclaw.gateway.observability

import io.jclaw.channel.ChannelAdapter
import io.jclaw.channel.ChannelRegistry
import spock.lang.Specification

class GatewayHealthIndicatorSpec extends Specification {

    def "reports UP when all adapters running"() {
        given:
        def registry = new ChannelRegistry()
        def adapter = Mock(ChannelAdapter) {
            channelId() >> "telegram"
            displayName() >> "Telegram"
            isRunning() >> true
        }
        registry.register(adapter)

        def metrics = new GatewayMetrics()
        def health = new GatewayHealthIndicator(registry, metrics)

        when:
        def result = health.health()

        then:
        result.status == "UP"
        result["channel.telegram"] == "UP"
        health.isHealthy()
    }

    def "reports DEGRADED when adapter not running"() {
        given:
        def registry = new ChannelRegistry()
        def adapter = Mock(ChannelAdapter) {
            channelId() >> "slack"
            displayName() >> "Slack"
            isRunning() >> false
        }
        registry.register(adapter)

        def metrics = new GatewayMetrics()
        def health = new GatewayHealthIndicator(registry, metrics)

        when:
        def result = health.health()

        then:
        result.status == "DEGRADED"
        result["channel.slack"] == "DOWN"
        !health.isHealthy()
    }

    def "reports UNKNOWN when no adapters"() {
        given:
        def registry = new ChannelRegistry()
        def metrics = new GatewayMetrics()
        def health = new GatewayHealthIndicator(registry, metrics)

        when:
        def result = health.health()

        then:
        result.status == "UNKNOWN"
        result.channelCount == 0
    }

    def "includes metrics in health"() {
        given:
        def registry = new ChannelRegistry()
        def metrics = new GatewayMetrics()
        metrics.recordMessage("test")
        metrics.recordError("test")
        def health = new GatewayHealthIndicator(registry, metrics)

        when:
        def result = health.health()

        then:
        result.totalMessages == 1L
        result.totalErrors == 1L
    }
}
