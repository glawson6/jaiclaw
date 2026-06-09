package io.jaiclaw.pipeline

import spock.lang.Specification

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

class PipelineTransportAuthenticatorSpec extends Specification {

    PipelineTransportAuthenticator authenticator = new PipelineTransportAuthenticator()

    def "NONE auth type always passes"() {
        given:
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.NONE, null, null)

        when:
        authenticator.verify(auth, "body", Map.of(), "pipe", "stage")

        then:
        noExceptionThrown()
    }

    def "null auth passes through"() {
        when:
        authenticator.verify(null, "body", Map.of(), "pipe", "stage")

        then:
        noExceptionThrown()
    }

    def "HMAC_SHA256 with valid signature passes"() {
        given:
        String secret = "test-secret"
        String body = "test body content"
        String signature = computeHmac(secret, body)

        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.HMAC_SHA256, secret, "X-Hub-Signature-256")

        when:
        authenticator.verify(auth, body, Map.of("X-Hub-Signature-256", signature), "pipe", "stage")

        then:
        noExceptionThrown()
    }

    def "HMAC_SHA256 with invalid signature fails"() {
        given:
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.HMAC_SHA256, "secret", "X-Hub-Signature-256")

        when:
        authenticator.verify(auth, "body",
                Map.of("X-Hub-Signature-256", "sha256=invalid"), "pipe", "stage")

        then:
        PipelineSecurityException e = thrown()
        e.reason.contains("HMAC signature verification failed")
    }

    def "HMAC_SHA256 with missing header fails"() {
        given:
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.HMAC_SHA256, "secret", "X-Hub-Signature-256")

        when:
        authenticator.verify(auth, "body", Map.of(), "pipe", "stage")

        then:
        PipelineSecurityException e = thrown()
        e.reason.contains("Missing HMAC signature header")
    }

    def "BEARER_TOKEN with valid token passes"() {
        given:
        String token = "my-secret-token"
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.BEARER_TOKEN, token, "X-Pipeline-Token")

        when:
        authenticator.verify(auth, "body",
                Map.of("X-Pipeline-Token", token), "pipe", "stage")

        then:
        noExceptionThrown()
    }

    def "BEARER_TOKEN with invalid token fails"() {
        given:
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.BEARER_TOKEN, "correct-token", "X-Pipeline-Token")

        when:
        authenticator.verify(auth, "body",
                Map.of("X-Pipeline-Token", "wrong-token"), "pipe", "stage")

        then:
        PipelineSecurityException e = thrown()
        e.reason.contains("Bearer token verification failed")
    }

    def "BEARER_TOKEN with Authorization Bearer prefix strips prefix"() {
        given:
        String token = "my-secret-token"
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.BEARER_TOKEN, token, "Authorization")

        when:
        authenticator.verify(auth, "body",
                Map.of("Authorization", "Bearer " + token), "pipe", "stage")

        then:
        noExceptionThrown()
    }

    def "case-insensitive header lookup"() {
        given:
        String token = "my-token"
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.BEARER_TOKEN, token, "X-Pipeline-Token")

        when:
        authenticator.verify(auth, "body",
                Map.of("x-pipeline-token", token), "pipe", "stage")

        then:
        noExceptionThrown()
    }

    def "custom headerName override works"() {
        given:
        String token = "my-token"
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.BEARER_TOKEN, token, "X-Custom-Auth")

        when:
        authenticator.verify(auth, "body",
                Map.of("X-Custom-Auth", token), "pipe", "stage")

        then:
        noExceptionThrown()
    }

    def "BEARER_TOKEN with missing header fails"() {
        given:
        StageDefinition.TransportAuth auth = new StageDefinition.TransportAuth(
                TransportAuthType.BEARER_TOKEN, "token", "X-Pipeline-Token")

        when:
        authenticator.verify(auth, "body", Map.of(), "pipe", "stage")

        then:
        PipelineSecurityException e = thrown()
        e.reason.contains("Missing bearer token header")
    }

    private static String computeHmac(String secret, String body) {
        Mac mac = Mac.getInstance("HmacSHA256")
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
        byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8))
        return "sha256=" + HexFormat.of().formatHex(hash)
    }
}
