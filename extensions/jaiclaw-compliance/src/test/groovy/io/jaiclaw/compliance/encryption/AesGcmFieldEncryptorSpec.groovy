package io.jaiclaw.compliance.encryption

import io.jaiclaw.core.encryption.FieldEncryptor
import spock.lang.Specification

class AesGcmFieldEncryptorSpec extends Specification {

    def "round-trip encrypt/decrypt returns original plaintext"() {
        given:
        FieldEncryptor enc = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())

        expect:
        enc.decrypt(enc.encrypt("hello world")) == "hello world"
        enc.decrypt(enc.encrypt("")) == ""
    }

    def "null in → null out (both directions)"() {
        given:
        FieldEncryptor enc = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())

        expect:
        enc.encrypt(null) == null
        enc.decrypt(null) == null
    }

    def "each encrypt produces a distinct ciphertext (fresh nonce)"() {
        given:
        FieldEncryptor enc = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())

        expect:
        enc.encrypt("same") != enc.encrypt("same")
    }

    def "decrypting with the wrong key raises EncryptionException"() {
        given:
        FieldEncryptor a = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())
        FieldEncryptor b = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())
        String ct = a.encrypt("secret")

        when:
        b.decrypt(ct)

        then:
        thrown(FieldEncryptor.EncryptionException)
    }

    def "key must be exactly 32 bytes"() {
        when:
        new AesGcmFieldEncryptor(new byte[bytes])

        then:
        thrown(IllegalArgumentException)

        where:
        bytes << [0, 8, 16, 24, 31, 33, 64]
    }

    def "tampered ciphertext is rejected"() {
        given:
        FieldEncryptor enc = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())
        String ct = enc.encrypt("secret")
        // Flip the last character to invalidate the auth tag.
        String tampered = ct.substring(0, ct.length() - 2) + (ct.charAt(ct.length() - 2) == 'a' as char ? 'b' : 'a') + ct.charAt(ct.length() - 1)

        when:
        enc.decrypt(tampered)

        then:
        thrown(FieldEncryptor.EncryptionException)
    }
}
