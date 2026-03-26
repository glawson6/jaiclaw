package io.jaiclaw.tools.security

import spock.lang.Shared
import spock.lang.Specification

class BootstrapValidatorSpec extends Specification {

    @Shared
    def cryptoService = new CryptoService()

    // ── ApiKeyBootstrapValidator ──

    def "API key validator accepts matching key"() {
        given:
        def validator = new ApiKeyBootstrapValidator()
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.API_KEY,
                "secret-key-123", null, null)

        when:
        def valid = validator.validate([apiKey: "secret-key-123"], props)

        then:
        valid
    }

    def "API key validator rejects wrong key"() {
        given:
        def validator = new ApiKeyBootstrapValidator()
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.API_KEY,
                "secret-key-123", null, null)

        when:
        def valid = validator.validate([apiKey: "wrong-key"], props)

        then:
        !valid
    }

    def "API key validator rejects missing key"() {
        given:
        def validator = new ApiKeyBootstrapValidator()
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.API_KEY,
                "secret-key-123", null, null)

        when:
        def valid = validator.validate([:], props)

        then:
        !valid
    }

    def "API key validator rejects blank key"() {
        given:
        def validator = new ApiKeyBootstrapValidator()
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.API_KEY,
                "secret-key-123", null, null)

        when:
        def valid = validator.validate([apiKey: "  "], props)

        then:
        !valid
    }

    def "API key validator rejects when no configured key"() {
        given:
        def validator = new ApiKeyBootstrapValidator()
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.API_KEY,
                null, null, null)

        when:
        def valid = validator.validate([apiKey: "any-key"], props)

        then:
        !valid
    }

    def "API key validator has descriptive failure reason"() {
        expect:
        new ApiKeyBootstrapValidator().failureReason().contains("API key")
    }

    // ── ClientCertBootstrapValidator ──

    def "Client cert validator accepts valid signature from registered key"() {
        given:
        def validator = new ClientCertBootstrapValidator(cryptoService)
        def keyPair = cryptoService.generateP256KeyPair()
        def publicKeyEncoded = cryptoService.encodePublicKey(keyPair.public)
        def nonce = cryptoService.generateNonce()

        // Sign the nonce with the private key
        def signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(nonce.getBytes("UTF-8"))
        def signature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())

        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.CLIENT_CERT,
                null, [publicKeyEncoded], null)

        when:
        def valid = validator.validate([
            clientPublicKey: publicKeyEncoded,
            clientNonce: nonce,
            clientSignature: signature
        ], props)

        then:
        valid
    }

    def "Client cert validator rejects unregistered public key"() {
        given:
        def validator = new ClientCertBootstrapValidator(cryptoService)
        def keyPair = cryptoService.generateP256KeyPair()
        def otherKeyPair = cryptoService.generateP256KeyPair()
        def publicKeyEncoded = cryptoService.encodePublicKey(keyPair.public)
        def otherKeyEncoded = cryptoService.encodePublicKey(otherKeyPair.public)
        def nonce = "test-nonce"

        def signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(nonce.getBytes("UTF-8"))
        def signature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())

        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.CLIENT_CERT,
                null, [otherKeyEncoded], null)

        when:
        def valid = validator.validate([
            clientPublicKey: publicKeyEncoded,
            clientNonce: nonce,
            clientSignature: signature
        ], props)

        then:
        !valid
    }

    def "Client cert validator rejects invalid signature"() {
        given:
        def validator = new ClientCertBootstrapValidator(cryptoService)
        def keyPair = cryptoService.generateP256KeyPair()
        def publicKeyEncoded = cryptoService.encodePublicKey(keyPair.public)

        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.CLIENT_CERT,
                null, [publicKeyEncoded], null)

        when:
        def valid = validator.validate([
            clientPublicKey: publicKeyEncoded,
            clientNonce: "test-nonce",
            clientSignature: "invalid-signature"
        ], props)

        then:
        !valid
    }

    def "Client cert validator rejects missing parameters"() {
        given:
        def validator = new ClientCertBootstrapValidator(cryptoService)
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.CLIENT_CERT,
                null, ["some-key"], null)

        when:
        def valid = validator.validate([:], props)

        then:
        !valid
    }

    def "Client cert validator rejects empty allowed keys"() {
        given:
        def validator = new ClientCertBootstrapValidator(cryptoService)
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.CLIENT_CERT,
                null, [], null)

        when:
        def valid = validator.validate([clientPublicKey: "key", clientNonce: "n", clientSignature: "s"], props)

        then:
        !valid
    }

    // ── MutualBootstrapValidator ──

    def "Mutual validator accepts valid API key when no client keys configured"() {
        given:
        def validator = new MutualBootstrapValidator(cryptoService)
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.MUTUAL,
                "mutual-key-123", [], null)

        when:
        def valid = validator.validate([apiKey: "mutual-key-123"], props)

        then:
        valid
    }

    def "Mutual validator rejects wrong API key"() {
        given:
        def validator = new MutualBootstrapValidator(cryptoService)
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.MUTUAL,
                "mutual-key-123", [], null)

        when:
        def valid = validator.validate([apiKey: "wrong"], props)

        then:
        !valid
    }

    def "Mutual validator requires both API key and client cert when client keys configured"() {
        given:
        def validator = new MutualBootstrapValidator(cryptoService)
        def keyPair = cryptoService.generateP256KeyPair()
        def publicKeyEncoded = cryptoService.encodePublicKey(keyPair.public)
        def nonce = cryptoService.generateNonce()

        def signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(nonce.getBytes("UTF-8"))
        def signature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())

        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.MUTUAL,
                "mutual-key-123", [publicKeyEncoded], null)

        when:
        def valid = validator.validate([
            apiKey: "mutual-key-123",
            clientPublicKey: publicKeyEncoded,
            clientNonce: nonce,
            clientSignature: signature
        ], props)

        then:
        valid
    }

    def "Mutual validator has descriptive failure reason on API key failure"() {
        given:
        def validator = new MutualBootstrapValidator(cryptoService)
        def props = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, BootstrapTrust.MUTUAL,
                "key", [], null)

        when:
        validator.validate([apiKey: "wrong"], props)

        then:
        validator.failureReason().contains("API key")
    }

    // ── BootstrapTrust enum ──

    def "BootstrapTrust has 3 values"() {
        expect:
        BootstrapTrust.values().length == 3
        BootstrapTrust.API_KEY != null
        BootstrapTrust.CLIENT_CERT != null
        BootstrapTrust.MUTUAL != null
    }
}
