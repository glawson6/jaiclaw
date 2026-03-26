package io.jaiclaw.tools.security

import spock.lang.Specification
import spock.lang.Subject

class CryptoServiceSpec extends Specification {

    @Subject
    def cryptoService = new CryptoService()

    def "P-256 key pair generation produces valid key pair"() {
        when:
        def kp = cryptoService.generateP256KeyPair()

        then:
        kp.private != null
        kp.public != null
        kp.public.algorithm == "EC"
    }

    def "X25519 key pair generation produces valid key pair"() {
        when:
        def kp = cryptoService.generateX25519KeyPair()

        then:
        kp.private != null
        kp.public != null
        kp.public.algorithm == "X25519" || kp.public.algorithm == "XDH"
    }

    def "P-256 ECDH key agreement produces matching shared secrets"() {
        given:
        def alice = cryptoService.generateP256KeyPair()
        def bob = cryptoService.generateP256KeyPair()

        when:
        def secretAlice = cryptoService.keyAgreement(alice.private, bob.public, "ECDH")
        def secretBob = cryptoService.keyAgreement(bob.private, alice.public, "ECDH")

        then:
        secretAlice == secretBob
        secretAlice.length > 0
    }

    def "X25519 XDH key agreement produces matching shared secrets"() {
        given:
        def alice = cryptoService.generateX25519KeyPair()
        def bob = cryptoService.generateX25519KeyPair()

        when:
        def secretAlice = cryptoService.keyAgreement(alice.private, bob.public, "XDH")
        def secretBob = cryptoService.keyAgreement(bob.private, alice.public, "XDH")

        then:
        secretAlice == secretBob
        secretAlice.length > 0
    }

    def "public key encode/decode round-trip for P-256"() {
        given:
        def kp = cryptoService.generateP256KeyPair()

        when:
        def encoded = cryptoService.encodePublicKey(kp.public)
        def decoded = cryptoService.decodePublicKey(encoded, "EC")

        then:
        decoded.encoded == kp.public.encoded
    }

    def "public key encode/decode round-trip for X25519"() {
        given:
        def kp = cryptoService.generateX25519KeyPair()

        when:
        def encoded = cryptoService.encodePublicKey(kp.public)
        def decoded = cryptoService.decodePublicKey(encoded, "X25519")

        then:
        decoded.encoded == kp.public.encoded
    }

    def "HMAC-SHA256 sign and verify"() {
        given:
        def key = new byte[32]
        new Random(42).nextBytes(key)
        def data = "hello world"

        when:
        def signature = cryptoService.hmacSign(key, data)

        then:
        cryptoService.hmacVerify(key, data, signature)
    }

    def "HMAC-SHA256 verify rejects wrong signature"() {
        given:
        def key = new byte[32]
        new Random(42).nextBytes(key)

        expect:
        !cryptoService.hmacVerify(key, "hello", "wrong-signature")
    }

    def "HMAC-SHA256 verify rejects wrong data"() {
        given:
        def key = new byte[32]
        new Random(42).nextBytes(key)
        def signature = cryptoService.hmacSign(key, "original")

        expect:
        !cryptoService.hmacVerify(key, "tampered", signature)
    }

    def "nonces are unique"() {
        when:
        def nonces = (1..100).collect { cryptoService.generateNonce() }

        then:
        nonces.unique().size() == 100
    }

    def "nonces are Base64url-encoded 32 bytes"() {
        when:
        def nonce = cryptoService.generateNonce()
        def decoded = Base64.urlDecoder.decode(nonce)

        then:
        decoded.length == 32
    }

    def "SHA-256 fingerprint produces hex string"() {
        when:
        def fp = cryptoService.sha256Fingerprint("test".bytes)

        then:
        fp.length() == 64
        fp ==~ /[0-9a-f]{64}/
    }

    def "SHA-256 fingerprint is deterministic"() {
        expect:
        cryptoService.sha256Fingerprint("test".bytes) == cryptoService.sha256Fingerprint("test".bytes)
    }

    def "createSessionToken produces a valid JWT"() {
        given:
        def secret = new byte[32]
        new Random(42).nextBytes(secret)

        when:
        def token = cryptoService.createSessionToken(secret, "client-1", [handshakeId: "hs-1"], 3600)

        then:
        token != null
        token.split('\\.').length == 3 // header.payload.signature
    }
}
