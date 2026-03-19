package io.jclaw.gateway.routing

import io.jclaw.core.model.ChatType
import io.jclaw.core.model.RoutingBinding
import spock.lang.Specification

class RoutingServiceSpec extends Specification {

    def "always processes direct messages"() {
        given:
        def service = new RoutingService([], "mention-only")

        expect:
        service.shouldProcess("telegram", "123", ChatType.DIRECT, [] as Set, "bot1")
    }

    def "processes group message when mentioned"() {
        given:
        def service = new RoutingService([], "mention-only")

        expect:
        service.shouldProcess("slack", "C123", ChatType.GROUP, ["BOT1"] as Set, "BOT1")
    }

    def "drops group message when not mentioned with mention-only default"() {
        given:
        def service = new RoutingService([], "mention-only")

        expect:
        !service.shouldProcess("slack", "C123", ChatType.GROUP, [] as Set, "BOT1")
    }

    def "always processes group messages when default is 'always'"() {
        given:
        def service = new RoutingService([], "always")

        expect:
        service.shouldProcess("telegram", "-1001", ChatType.GROUP, [] as Set, "bot1")
    }

    def "ignores all group messages when default is 'ignore'"() {
        given:
        def service = new RoutingService([], "ignore")

        expect:
        !service.shouldProcess("telegram", "-1001", ChatType.GROUP, ["bot1"] as Set, "bot1")
    }

    def "binding overrides default behavior"() {
        given:
        def binding = new RoutingBinding("agent1", "telegram", "group", "-1001", false)
        def service = new RoutingService([binding], "mention-only")

        expect: "binding says mentionOnly=false, so always process"
        service.shouldProcess("telegram", "-1001", ChatType.GROUP, [] as Set, "bot1")
    }

    def "resolveAgentId returns binding agent"() {
        given:
        def binding = new RoutingBinding("custom-agent", "slack", "group", null, true)
        def service = new RoutingService([binding], "mention-only")

        expect:
        service.resolveAgentId("slack", "C123", ChatType.GROUP) == "custom-agent"
    }

    def "resolveAgentId returns default when no binding matches"() {
        given:
        def service = new RoutingService([], "mention-only")

        expect:
        service.resolveAgentId("telegram", "123", ChatType.DIRECT) == "default"
    }
}
