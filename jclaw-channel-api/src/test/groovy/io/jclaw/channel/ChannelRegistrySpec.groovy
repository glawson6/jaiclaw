package io.jclaw.channel

import spock.lang.Specification

class ChannelRegistrySpec extends Specification {

    ChannelRegistry registry = new ChannelRegistry()

    def "register and retrieve adapter"() {
        given:
        def adapter = mockAdapter("telegram")

        when:
        registry.register(adapter)

        then:
        registry.get("telegram").isPresent()
        registry.get("telegram").get() == adapter
        registry.size() == 1
        registry.contains("telegram")
    }

    def "get returns empty for unknown channel"() {
        expect:
        registry.get("unknown").isEmpty()
        !registry.contains("unknown")
    }

    def "duplicate registration throws"() {
        given:
        registry.register(mockAdapter("telegram"))

        when:
        registry.register(mockAdapter("telegram"))

        then:
        thrown(IllegalStateException)
    }

    def "all returns all registered adapters"() {
        given:
        registry.register(mockAdapter("telegram"))
        registry.register(mockAdapter("slack"))
        registry.register(mockAdapter("discord"))

        expect:
        registry.all().size() == 3
        registry.channelIds() == ["telegram", "slack", "discord"] as Set
    }

    def "unregister removes adapter and stops if running"() {
        given:
        def adapter = mockAdapter("telegram")
        adapter.isRunning() >> true
        registry.register(adapter)

        when:
        registry.unregister("telegram")

        then:
        1 * adapter.stop()
        !registry.contains("telegram")
        registry.size() == 0
    }

    def "startAll starts all adapters with handler"() {
        given:
        def telegram = mockAdapter("telegram")
        def slack = mockAdapter("slack")
        registry.register(telegram)
        registry.register(slack)
        def handler = Mock(ChannelMessageHandler)

        when:
        registry.startAll(handler)

        then:
        1 * telegram.start(handler)
        1 * slack.start(handler)
    }

    def "stopAll stops running adapters"() {
        given:
        def telegram = mockAdapter("telegram")
        telegram.isRunning() >> true
        def slack = mockAdapter("slack")
        slack.isRunning() >> false
        registry.register(telegram)
        registry.register(slack)

        when:
        registry.stopAll()

        then:
        1 * telegram.stop()
        0 * slack.stop()
    }

    def "startAll tolerates adapter start failure"() {
        given:
        def bad = mockAdapter("bad")
        bad.start(_) >> { throw new RuntimeException("fail") }
        def good = mockAdapter("good")
        registry.register(bad)
        registry.register(good)
        def handler = Mock(ChannelMessageHandler)

        when:
        registry.startAll(handler)

        then:
        noExceptionThrown()
        1 * good.start(handler)
    }

    private ChannelAdapter mockAdapter(String id) {
        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> id
        adapter.displayName() >> id.capitalize()
        return adapter
    }
}
