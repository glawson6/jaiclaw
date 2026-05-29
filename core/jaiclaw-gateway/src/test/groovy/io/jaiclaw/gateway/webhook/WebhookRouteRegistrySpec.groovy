package io.jaiclaw.gateway.webhook

import spock.lang.Specification

class WebhookRouteRegistrySpec extends Specification {

    def registry = new WebhookRouteRegistry()

    def "register and find route by path"() {
        given:
        def route = new WebhookRoute("github", WebhookAuthType.NONE, null, { e -> "ok" })
        registry.register(route)

        expect:
        registry.findByPath("github").isPresent()
        registry.findByPath("github").get().path() == "github"
    }

    def "normalizes paths with leading slash"() {
        given:
        registry.register(new WebhookRoute("stripe/events", WebhookAuthType.NONE, null, { e -> "ok" }))

        expect:
        registry.findByPath("/stripe/events").isPresent()
    }

    def "unregister removes route"() {
        given:
        registry.register(new WebhookRoute("test", WebhookAuthType.NONE, null, { e -> "ok" }))

        when:
        def removed = registry.unregister("test")

        then:
        removed
        registry.findByPath("test").isEmpty()
    }

    def "returns empty for unknown path"() {
        expect:
        registry.findByPath("nonexistent").isEmpty()
    }
}
