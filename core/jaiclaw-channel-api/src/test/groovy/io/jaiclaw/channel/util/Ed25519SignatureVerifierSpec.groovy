package io.jaiclaw.channel.util

import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.HexFormat

/**
 * Verified against real Ed25519 signatures generated in-test, not against
 * recorded fixtures — a verifier that only accepts hand-made vectors proves
 * nothing about live Discord traffic.
 */
class Ed25519SignatureVerifierSpec extends Specification {

    @Shared KeyPair keyPair
    @Shared String publicKeyHex

    def setupSpec() {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        // Discord publishes the key as 32 raw hex bytes; X.509 encoding puts
        // them in the trailing 32 bytes.
        byte[] encoded = keyPair.public.encoded
        byte[] raw = new byte[32]
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32)
        publicKeyHex = HexFormat.of().formatHex(raw)
    }

    /** Signs exactly what Discord signs: timestamp || body. */
    private String sign(String timestamp, String body) {
        def signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(timestamp.getBytes(StandardCharsets.UTF_8))
        signer.update(body.getBytes(StandardCharsets.UTF_8))
        HexFormat.of().formatHex(signer.sign())
    }

    def "a correctly signed request verifies"() {
        given:
        def ts = "1700000000"
        def body = '{"type":1}'

        expect:
        Ed25519SignatureVerifier.verify(publicKeyHex, sign(ts, body), ts, body)
    }

    def "a tampered body is rejected"() {
        given: "signed as a PING, delivered as a command"
        def ts = "1700000000"
        def signature = sign(ts, '{"type":1}')

        expect:
        !Ed25519SignatureVerifier.verify(publicKeyHex, signature, ts,
                '{"type":2,"data":{"content":"rm -rf /"}}')
    }

    def "a replayed signature with a different timestamp is rejected"() {
        given: "the timestamp is inside the signed payload, so it cannot be swapped"
        def body = '{"type":1}'
        def signature = sign("1700000000", body)

        expect:
        !Ed25519SignatureVerifier.verify(publicKeyHex, signature, "1700009999", body)
    }

    def "a signature from a different key is rejected"() {
        given: "an attacker signing with their own key"
        def other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        def ts = "1700000000"
        def body = '{"type":1}'
        def signer = Signature.getInstance("Ed25519")
        signer.initSign(other.private)
        signer.update(ts.getBytes(StandardCharsets.UTF_8))
        signer.update(body.getBytes(StandardCharsets.UTF_8))
        def forged = HexFormat.of().formatHex(signer.sign())

        expect:
        !Ed25519SignatureVerifier.verify(publicKeyHex, forged, ts, body)
    }

    def "whitespace differences in the body break verification"() {
        given: "why the adapter must verify the raw body, not a re-serialised one"
        def ts = "1700000000"
        def signature = sign(ts, '{"type":1}')

        expect:
        !Ed25519SignatureVerifier.verify(publicKeyHex, signature, ts, '{ "type": 1 }')
    }

    def "missing or blank inputs fail closed"() {
        expect:
        !Ed25519SignatureVerifier.verify(key, sig, ts, body)

        where:
        key           | sig      | ts           | body
        null          | "ab"     | "1"          | "{}"
        ""            | "ab"     | "1"          | "{}"
        "aabb"        | null     | "1"          | "{}"
        "aabb"        | ""       | "1"          | "{}"
        "aabb"        | "ab"     | null         | "{}"
        "aabb"        | "ab"     | ""           | "{}"
        "aabb"        | "ab"     | "1"          | null
    }

    def "a non-hex signature or key fails closed rather than throwing"() {
        when:
        def badKey = Ed25519SignatureVerifier.verify("not-hex!!", "aabb", "1", "{}")
        def badSig = Ed25519SignatureVerifier.verify(publicKeyHex, "not-hex!!", "1", "{}")

        then: "a verifier that throws ends up wrapped in a catch that proceeds anyway"
        noExceptionThrown()
        !badKey
        !badSig
    }

    def "a wrong-length public key is rejected"() {
        expect: "the likeliest misconfiguration is pasting the bot token here"
        Ed25519SignatureVerifier.parsePublicKey("aabbcc") == null
        Ed25519SignatureVerifier.parsePublicKey(publicKeyHex + "aabb") == null
    }

    def "a wrong-length signature is rejected"() {
        given:
        def ts = "1700000000"
        def body = '{"type":1}'
        def valid = sign(ts, body)

        expect: "Ed25519 signatures are exactly 64 bytes"
        !Ed25519SignatureVerifier.verify(publicKeyHex, valid[0..-5], ts, body)
    }

    def "a valid 32-byte key parses"() {
        expect:
        Ed25519SignatureVerifier.parsePublicKey(publicKeyHex) != null
    }

    def "surrounding whitespace in the key is tolerated"() {
        given: "copy-paste from the developer portal often carries a newline"
        def ts = "1700000000"
        def body = '{"type":1}'

        expect:
        Ed25519SignatureVerifier.verify("  " + publicKeyHex + "\n", sign(ts, body), ts, body)

        and: "a stray non-whitespace character is NOT silently ignored"
        !Ed25519SignatureVerifier.verify(publicKeyHex + "x", sign(ts, body), ts, body)
    }
}
