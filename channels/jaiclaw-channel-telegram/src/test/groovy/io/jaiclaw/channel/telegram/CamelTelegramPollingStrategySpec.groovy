package io.jaiclaw.channel.telegram

import com.fasterxml.jackson.databind.JsonNode
import org.apache.camel.CamelContext
import org.apache.camel.RoutesBuilder
import org.apache.camel.spi.RouteController
import spock.lang.Specification

class CamelTelegramPollingStrategySpec extends Specification {

    CamelContext camelContext = Mock(CamelContext)
    RouteController routeController = Mock(RouteController)

    def setup() {
        camelContext.getRouteController() >> routeController
    }

    def "startPolling adds route to CamelContext"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 30)
        def strategy = new CamelTelegramPollingStrategy(camelContext)

        when:
        strategy.startPolling(config, { JsonNode update -> })

        then:
        1 * camelContext.addRoutes(_ as RoutesBuilder)
        strategy.isPolling()
    }

    def "stopPolling stops and removes the route"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 30)
        def strategy = new CamelTelegramPollingStrategy(camelContext)
        strategy.startPolling(config, { JsonNode update -> })

        when:
        strategy.stopPolling()

        then:
        1 * routeController.stopRoute("jaiclaw-telegram-poller")
        1 * camelContext.removeRoute("jaiclaw-telegram-poller")
        !strategy.isPolling()
    }

    def "stopPolling is idempotent"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 30)
        def strategy = new CamelTelegramPollingStrategy(camelContext)
        strategy.startPolling(config, { JsonNode update -> })

        when:
        strategy.stopPolling()
        strategy.stopPolling()

        then:
        1 * routeController.stopRoute("jaiclaw-telegram-poller")
        1 * camelContext.removeRoute("jaiclaw-telegram-poller")
        !strategy.isPolling()
    }

    def "startPolling is idempotent"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 30)
        def strategy = new CamelTelegramPollingStrategy(camelContext)

        when:
        strategy.startPolling(config, { JsonNode update -> })
        strategy.startPolling(config, { JsonNode update -> })

        then:
        1 * camelContext.addRoutes(_ as RoutesBuilder)
        strategy.isPolling()
    }

    def "startPolling sets polling to false on failure"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 30)
        def strategy = new CamelTelegramPollingStrategy(camelContext)
        camelContext.addRoutes(_) >> { throw new Exception("Camel route failure") }

        when:
        strategy.startPolling(config, { JsonNode update -> })

        then:
        thrown(IllegalStateException)
        !strategy.isPolling()
    }
}
