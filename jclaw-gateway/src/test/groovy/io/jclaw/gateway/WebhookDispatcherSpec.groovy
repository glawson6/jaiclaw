package io.jclaw.gateway

import org.springframework.http.ResponseEntity
import spock.lang.Specification

class WebhookDispatcherSpec extends Specification {

    WebhookDispatcher dispatcher = new WebhookDispatcher()

    def "dispatch to registered handler"() {
        given:
        dispatcher.register("telegram", { body, headers ->
            ResponseEntity.ok("handled: " + body)
        })

        when:
        def response = dispatcher.dispatch("telegram", '{"update_id":1}', Map.of())

        then:
        response.statusCode.value() == 200
        response.body == 'handled: {"update_id":1}'
    }

    def "dispatch to unknown channel returns 404"() {
        when:
        def response = dispatcher.dispatch("unknown", "{}", Map.of())

        then:
        response.statusCode.value() == 404
    }

    def "dispatch handles handler exception gracefully"() {
        given:
        dispatcher.register("bad", { body, headers ->
            throw new RuntimeException("kaboom")
        })

        when:
        def response = dispatcher.dispatch("bad", "{}", Map.of())

        then:
        response.statusCode.value() == 500
    }

    def "registeredChannels returns all registered channel IDs"() {
        given:
        dispatcher.register("telegram", { b, h -> ResponseEntity.ok("") })
        dispatcher.register("slack", { b, h -> ResponseEntity.ok("") })

        expect:
        dispatcher.registeredChannels() == ["telegram", "slack"] as Set
    }
}
