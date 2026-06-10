package io.jaiclaw.channel.util

import spock.lang.Specification

/**
 * 0.8.0 P3.3: locks the consolidated HMAC verification utility.
 */
class WebhookSignatureUtilSpec extends Specification {

    def "computeHmacSha256 produces a stable hex string"() {
        when:
        String sig1 = WebhookSignatureUtil.computeHmacSha256("secret", "hello")
        String sig2 = WebhookSignatureUtil.computeHmacSha256("secret", "hello")

        then:
        sig1 == sig2
        sig1.length() == 64        // 32 bytes hex
        sig1 ==~ /^[0-9a-f]+$/
    }

    def "computeHmacSha256 differs for different inputs"() {
        expect:
        WebhookSignatureUtil.computeHmacSha256("secret", "a") !=
                WebhookSignatureUtil.computeHmacSha256("secret", "b")
        WebhookSignatureUtil.computeHmacSha256("s1", "x") !=
                WebhookSignatureUtil.computeHmacSha256("s2", "x")
    }

    def "computeHmacSha256 rejects null inputs"() {
        when:
        WebhookSignatureUtil.computeHmacSha256(secret, payload)

        then:
        thrown(IllegalArgumentException)

        where:
        secret   | payload
        null     | "x"
        "k"      | null
    }

    def "verifyHmacSha256 accepts the matching signature"() {
        given:
        String expected = WebhookSignatureUtil.computeHmacSha256("k", "payload")

        expect:
        WebhookSignatureUtil.verifyHmacSha256("k", "payload", expected)
    }

    def "verifyHmacSha256 rejects altered payload"() {
        given:
        String expected = WebhookSignatureUtil.computeHmacSha256("k", "payload")

        expect:
        !WebhookSignatureUtil.verifyHmacSha256("k", "payload-tampered", expected)
    }

    def "verifyHmacSha256 rejects wrong key"() {
        given:
        String expected = WebhookSignatureUtil.computeHmacSha256("k", "payload")

        expect:
        !WebhookSignatureUtil.verifyHmacSha256("other-k", "payload", expected)
    }

    def "verifyHmacSha256 returns false for null inputs"() {
        expect:
        !WebhookSignatureUtil.verifyHmacSha256(null, "p", "sig")
        !WebhookSignatureUtil.verifyHmacSha256("k", null, "sig")
        !WebhookSignatureUtil.verifyHmacSha256("k", "p", null)
    }

    def "verifySlackSignature accepts a well-formed Slack signature"() {
        given:
        long now = System.currentTimeMillis() / 1000L
        String ts = String.valueOf(now)
        String body = '{"event":"x"}'
        String expected = "v0=" + WebhookSignatureUtil.computeHmacSha256(
                "slack-secret", "v0:" + ts + ":" + body)

        expect:
        WebhookSignatureUtil.verifySlackSignature("slack-secret", ts, body, expected, 300L)
    }

    def "verifySlackSignature rejects an expired timestamp"() {
        given:
        long way_in_the_past = (System.currentTimeMillis() / 1000L) - 10_000L
        String ts = String.valueOf(way_in_the_past)
        String body = "x"
        String expected = "v0=" + WebhookSignatureUtil.computeHmacSha256(
                "slack-secret", "v0:" + ts + ":" + body)

        expect:
        !WebhookSignatureUtil.verifySlackSignature("slack-secret", ts, body, expected, 300L)
    }

    def "verifySlackSignature rejects a non-numeric timestamp"() {
        expect:
        !WebhookSignatureUtil.verifySlackSignature("k", "not-a-number", "body", "v0=deadbeef", 300L)
    }

    def "verifySlackSignature rejects a forged signature"() {
        given:
        String ts = String.valueOf(System.currentTimeMillis() / 1000L)

        expect:
        !WebhookSignatureUtil.verifySlackSignature("slack-secret", ts, "body", "v0=forged", 300L)
    }

    def "constantTimeEquals compares strings"() {
        expect:
        WebhookSignatureUtil.constantTimeEquals("abc", "abc")
        !WebhookSignatureUtil.constantTimeEquals("abc", "abd")
        !WebhookSignatureUtil.constantTimeEquals("abc", "abcd")
        !WebhookSignatureUtil.constantTimeEquals(null, "abc")
        !WebhookSignatureUtil.constantTimeEquals("abc", null)
    }
}
