package io.jaiclaw.compliance.encryption

import io.jaiclaw.core.secrets.SecretResolution
import io.jaiclaw.core.secrets.SecretsResolver
import spock.lang.Specification

import java.util.HexFormat

class EncryptionKeyResolverSpec extends Specification {

    static final byte[] KEY = AesGcmFieldEncryptor.generateKey()
    static final String KEY_B64 = Base64.encoder.encodeToString(KEY)
    static final String KEY_HEX = HexFormat.of().formatHex(KEY)

    def "resolves a base64 key from the property"() {
        expect:
        EncryptionKeyResolver.resolve(KEY_B64, null) == KEY
    }

    def "resolves a hex key from the property"() {
        expect: "64-char hex also parses as 48-byte base64, so decode order matters"
        EncryptionKeyResolver.resolve(KEY_HEX, null) == KEY
    }

    def "hex is tried before base64 — a hex key must not be read as 48 bytes"() {
        when: "the same key in hex"
        byte[] resolved = EncryptionKeyResolver.resolve(KEY_HEX, null)

        then: "it decodes to 32 bytes, not the 48 a base64-first order would yield"
        resolved.length == 32
        resolved == KEY
    }

    def "tolerates surrounding whitespace — env vars and files pick it up easily"() {
        expect:
        EncryptionKeyResolver.resolve("  " + KEY_B64 + "\n", null) == KEY
    }

    def "falls back to the secrets provider when the property is absent"() {
        given:
        def resolver = Stub(SecretsResolver) {
            resolve(EncryptionKeyResolver.KEY_PROPERTY) >> new SecretResolution.Resolved(
                    EncryptionKeyResolver.KEY_PROPERTY, KEY_B64, "test-vault")
        }

        expect:
        EncryptionKeyResolver.resolve(null, resolver) == KEY
    }

    def "the property wins over the secrets provider"() {
        given: "a provider holding a DIFFERENT key"
        def other = Base64.encoder.encodeToString(AesGcmFieldEncryptor.generateKey())
        def resolver = Stub(SecretsResolver) {
            resolve(_) >> new SecretResolution.Resolved("k", other, "test-vault")
        }

        expect: "an explicit property is the operator's direct instruction"
        EncryptionKeyResolver.resolve(KEY_B64, resolver) == KEY
    }

    def "no key anywhere fails with a message naming the property"() {
        when:
        EncryptionKeyResolver.resolve(null, null)

        then: "silently running unencrypted would be worse than refusing to start"
        def e = thrown(IllegalStateException)
        e.message.contains(EncryptionKeyResolver.KEY_PROPERTY)
        e.message.contains("openssl rand")
    }

    def "a Missing resolution is reported as no key configured"() {
        given:
        def resolver = Stub(SecretsResolver) {
            resolve(_) >> new SecretResolution.Missing(EncryptionKeyResolver.KEY_PROPERTY)
        }

        when:
        EncryptionKeyResolver.resolve(null, resolver)

        then:
        thrown(IllegalStateException)
    }

    def "a provider error is surfaced, not masked as no-key-configured"() {
        given:
        def boom = new RuntimeException("vault unreachable")
        def resolver = Stub(SecretsResolver) {
            resolve(_) >> new SecretResolution.ProviderError("k", "test-vault", boom)
        }

        when:
        EncryptionKeyResolver.resolve(null, resolver)

        then: "an operator debugging a down vault must not be told the key is unset"
        def e = thrown(IllegalStateException)
        e.message.contains("test-vault")
        e.cause == boom
    }

    def "a passphrase is refused rather than stretched into a key"() {
        when:
        EncryptionKeyResolver.resolve(passphrase, null)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("32")

        where: "each of these is a plausible operator mistake"
        passphrase << [
                "correct-horse-battery-staple",
                "hunter2",
                "a".repeat(31),
                "a".repeat(33),
        ]
    }

    def "a 32-CHARACTER string is not a 32-byte key"() {
        given: "exactly 32 ASCII chars — the trap String.getBytes() falls into"
        def thirtyTwoChars = "0123456789abcdef0123456789abcdef"

        when:
        byte[] resolved = EncryptionKeyResolver.resolve(thirtyTwoChars, null)

        then: "it is valid hex, so it decodes to 16 bytes and is correctly refused"
        def e = thrown(IllegalStateException)
        e.message.contains("16 bytes")
    }

    def "a wrong-length base64 key is refused with the decoded length named"() {
        given:
        def shortKey = Base64.encoder.encodeToString(new byte[16])

        when:
        EncryptionKeyResolver.resolve(shortKey, null)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("16 bytes")
    }

    def "tryResolve returns empty instead of throwing, for pre-flight checks"() {
        expect:
        EncryptionKeyResolver.tryResolve(null, null).isEmpty()
        EncryptionKeyResolver.tryResolve(KEY_B64, null).isPresent()
    }

    def "a resolved key actually round-trips through the encryptor"() {
        given:
        def encryptor = new AesGcmFieldEncryptor(EncryptionKeyResolver.resolve(KEY_B64, null))

        when:
        def ciphertext = encryptor.encrypt("sensitive transcript body")

        then: "the whole point of the resolver is producing a usable key"
        ciphertext != "sensitive transcript body"
        encryptor.decrypt(ciphertext) == "sensitive transcript body"
    }
}
