package io.jaiclaw.gateway.mcp.auth

import spock.lang.Specification

class ProtectedResourceMetadataSpec extends Specification {

    def controller = new ProtectedResourceMetadataController(
            new ProtectedResourceMetadataProperties(true, null,
                    ["https://idp.example.com/oidc"],
                    ["jaiclaw:tools:coding", "jaiclaw:tools:full"], null),
            "https://jaiclaw.example.com/api",
            ["https://idp.example.com/oidc"])

    def "publishes the RFC 9728 document"() {
        when:
        def body = controller.metadata().body

        then:
        body.resource == "https://jaiclaw.example.com/api"
        body.authorization_servers == ["https://idp.example.com/oidc"]
        body.scopes_supported == ["jaiclaw:tools:coding", "jaiclaw:tools:full"]
    }

    def "advertises header-only bearer transport"() {
        expect: "a token in a query string lands in access logs, proxy logs and Referer headers"
        controller.metadata().body.bearer_methods_supported == ["header"]
    }

    def "omits optional members rather than emitting nulls"() {
        given:
        def minimal = new ProtectedResourceMetadataController(
                ProtectedResourceMetadataProperties.defaults(),
                "https://jaiclaw.example.com/api",
                ["https://idp.example.com/oidc"])

        when:
        def body = minimal.metadata().body

        then:
        !body.containsKey("scopes_supported")
        !body.containsKey("resource_documentation")
    }

    def "is cacheable — stable content, but short enough that a rotated issuer propagates"() {
        expect:
        controller.metadata().headers.getCacheControl().contains("max-age=3600")
    }

    def "derives an origin from a resource identifier"() {
        expect:
        McpAuthMetadataAutoConfiguration.originOf(url) == expected

        where:
        url                                        || expected
        "https://jaiclaw.example.com/api"          || "https://jaiclaw.example.com"
        "https://jaiclaw.example.com:8443/api"     || "https://jaiclaw.example.com:8443"
        "https://jaiclaw.example.com"              || "https://jaiclaw.example.com"
        "not-a-url"                                || null
        null                                       || null
        ""                                         || null
    }
}
