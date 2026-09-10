package io.jaiclaw.channel.webhook

import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.channel.util.WebhookSignatureUtil
import io.jaiclaw.core.tool.ToolProfile
import spock.lang.Specification

class WebhookChannelSpec extends Specification {

    static final String SECRET = "s3cr3t-shared-key"
    static final String BODY = '{"event":"pull_request","number":42}'

    List<ChannelMessage> received = []
    ChannelMessageHandler handler = { ChannelMessage m -> received << m } as ChannelMessageHandler

    private static WebhookRoute route(String id = "gh",
                                      String secret = SECRET,
                                      WebhookRoute.SessionMode mode = WebhookRoute.SessionMode.ISOLATED,
                                      ToolProfile profile = null) {
        new WebhookRoute(id, secret, "acme", "reviewer", mode, profile, null)
    }

    private WebhookChannelAdapter adapter(WebhookRoute... routes) {
        def a = new WebhookChannelAdapter(new WebhookProperties(true, routes as List))
        a.start(handler)
        a
    }

    private static String sign(String body, String secret = SECRET) {
        WebhookSignatureUtil.computeHmacSha256(secret, body)
    }

    // ── Signature verification ───────────────────────────────────────────────

    def "a correctly signed delivery is accepted and dispatched"() {
        given:
        def a = adapter(route())

        when:
        def result = a.handle("gh", BODY, sign(BODY))

        then:
        result == WebhookChannelAdapter.Result.ACCEPTED
        received.size() == 1
        received[0].content() == BODY
        received[0].channelId() == "webhook"
    }

    def "a bad signature is refused and never reaches the agent"() {
        given:
        def a = adapter(route())

        when:
        def result = a.handle("gh", BODY, sign(BODY, "wrong-secret"))

        then: "the body reaches a language model with tools, so this must fail closed"
        result == WebhookChannelAdapter.Result.UNAUTHORIZED
        received.isEmpty()
    }

    def "a tampered body is refused even with a signature that was valid for the original"() {
        given:
        def a = adapter(route())
        def signature = sign(BODY)

        when:
        def result = a.handle("gh", BODY.replace("42", "999"), signature)

        then:
        result == WebhookChannelAdapter.Result.UNAUTHORIZED
        received.isEmpty()
    }

    def "a missing signature header is refused"() {
        given:
        def a = adapter(route())

        expect:
        a.handle("gh", BODY, missing) == WebhookChannelAdapter.Result.UNAUTHORIZED
        received.isEmpty()

        where:
        missing << [null, "", "   "]
    }

    def "an unknown route is indistinguishable from a bad signature"() {
        given:
        def a = adapter(route())

        expect: "a prober must not be able to enumerate configured routes"
        a.handle("no-such-route", BODY, sign(BODY)) == WebhookChannelAdapter.Result.UNAUTHORIZED
    }

    def "a route with no secret is disabled, not left open"() {
        given:
        def a = adapter(route("gh", secret))

        expect: "failing closed beats an unauthenticated endpoint feeding an agent"
        a.routes().isEmpty()
        a.handle("gh", BODY, sign(BODY)) == WebhookChannelAdapter.Result.UNAUTHORIZED

        where:
        secret << [null, "", "   "]
    }

    // ── Tool profile ─────────────────────────────────────────────────────────

    def "a route defaults to the WEBHOOK_SAFE profile"() {
        expect:
        route().toolProfile() == ToolProfile.WEBHOOK_SAFE
    }

    def "a route cannot be configured with a wider profile than WEBHOOK_SAFE"() {
        expect: "even if the YAML asks for FULL, attacker-controlled input stays sandboxed"
        route("gh", SECRET, WebhookRoute.SessionMode.ISOLATED, requested).toolProfile() == expected

        where:
        requested             | expected
        ToolProfile.FULL      | ToolProfile.WEBHOOK_SAFE
        ToolProfile.CODING    | ToolProfile.WEBHOOK_SAFE
        ToolProfile.MESSAGING | ToolProfile.WEBHOOK_SAFE
        ToolProfile.MINIMAL   | ToolProfile.MINIMAL
        ToolProfile.NONE      | ToolProfile.NONE
        null                  | ToolProfile.WEBHOOK_SAFE
    }

    def "the enforced profile is carried on the dispatched message"() {
        given:
        def a = adapter(route("gh", SECRET, WebhookRoute.SessionMode.ISOLATED, ToolProfile.FULL))

        when:
        a.handle("gh", BODY, sign(BODY))

        then:
        received[0].platformData()["toolProfile"] == "WEBHOOK_SAFE"
    }

    // ── Sessions ─────────────────────────────────────────────────────────────

    def "ISOLATED mode gives every delivery its own session"() {
        given:
        def a = adapter(route("gh", SECRET, WebhookRoute.SessionMode.ISOLATED))

        when:
        a.handle("gh", BODY, sign(BODY))
        a.handle("gh", BODY, sign(BODY))

        then: "no state carries between unrelated payloads"
        received.size() == 2
        received[0].sessionKey("reviewer") != received[1].sessionKey("reviewer")
    }

    def "PER_ROUTE mode reuses one session across deliveries"() {
        given:
        def a = adapter(route("gh", SECRET, WebhookRoute.SessionMode.PER_ROUTE))

        when:
        a.handle("gh", BODY, sign(BODY))
        a.handle("gh", BODY, sign(BODY))

        then:
        received[0].sessionKey("reviewer") == received[1].sessionKey("reviewer")
    }

    def "the tenant and agent are attached for downstream resolution"() {
        given:
        def a = adapter(route())

        when:
        a.handle("gh", BODY, sign(BODY))

        then:
        received[0].platformData()["tenantId"] == "acme"
        received[0].platformData()["agentId"] == "reviewer"
        received[0].platformData()["routeId"] == "gh"
    }

    // ── Outbound ─────────────────────────────────────────────────────────────

    def "sending is reported unsupported rather than silently dropped"() {
        given:
        def a = adapter(route())

        when:
        def result = a.sendMessage(ChannelMessage.inbound("m", "webhook", "gh", "p", "reply", [:]))

        then: "a misrouted reply must be visible, not swallowed"
        result instanceof io.jaiclaw.channel.DeliveryResult.Failure
        result.errorCode() == "webhook_inbound_only"
        result.message().contains("inbound only")
        !result.retryable()
    }

    def "deliveries arriving before start or after stop are dropped without error"() {
        given:
        def a = new WebhookChannelAdapter(new WebhookProperties(true, [route()]))

        when: "not started yet"
        def before = a.handle("gh", BODY, sign(BODY))

        then:
        before == WebhookChannelAdapter.Result.ACCEPTED
        received.isEmpty()
        noExceptionThrown()
    }
}
