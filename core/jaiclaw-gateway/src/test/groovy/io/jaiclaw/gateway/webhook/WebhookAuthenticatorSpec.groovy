package io.jaiclaw.gateway.webhook

import spock.lang.Specification

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

class WebhookAuthenticatorSpec extends Specification {

    def "NONE auth always passes"() {
        given:
        def route = new WebhookRoute("test", WebhookAuthType.NONE, null, { e -> "ok" })

        expect:
        WebhookAuthenticator.verify(route, "body", [:])
    }

    def "HMAC_SHA256 verifies correct signature"() {
        given:
        def secret = "my-secret"
        def body = '{"action":"push"}'
        def signature = "sha256=" + hmacSha256(secret, body)
        def route = new WebhookRoute("github", WebhookAuthType.HMAC_SHA256, secret, { e -> "ok" })

        expect:
        WebhookAuthenticator.verify(route, body, ["x-hub-signature-256": signature])
    }

    def "BEARER_TOKEN verifies correct token"() {
        given:
        def token = "secret-token-123"
        def route = new WebhookRoute("api", WebhookAuthType.BEARER_TOKEN, token, { e -> "ok" })

        expect:
        WebhookAuthenticator.verify(route, "body", ["Authorization": "Bearer " + token])
        !WebhookAuthenticator.verify(route, "body", ["Authorization": "Bearer wrong-token"])
    }

    private static String hmacSha256(String secret, String data) {
        def mac = Mac.getInstance("HmacSHA256")
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
        def hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }
}
