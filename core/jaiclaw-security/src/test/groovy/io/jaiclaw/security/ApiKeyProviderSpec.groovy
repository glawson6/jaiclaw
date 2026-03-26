package io.jaiclaw.security

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ApiKeyProviderSpec extends Specification {

    @TempDir
    Path tempDir

    def "auto-generates key and writes to file when no explicit key and no file exists"() {
        given:
        Path keyFile = tempDir.resolve("subdir/api-key")

        when:
        def provider = new ApiKeyProvider(null, keyFile.toString())

        then:
        provider.source == "generated"
        provider.resolvedKey.startsWith("jaiclaw_ak_")
        provider.resolvedKey.length() == 43
        Files.readString(keyFile).trim() == provider.resolvedKey
    }

    def "reads existing key from file"() {
        given:
        Path keyFile = tempDir.resolve("api-key")
        Files.writeString(keyFile, "jaiclaw_ak_existingkey1234567890abcd\n")

        when:
        def provider = new ApiKeyProvider(null, keyFile.toString())

        then:
        provider.source == "file"
        provider.resolvedKey == "jaiclaw_ak_existingkey1234567890abcd"
    }

    def "explicit key overrides file"() {
        given:
        Path keyFile = tempDir.resolve("api-key")
        Files.writeString(keyFile, "jaiclaw_ak_fromfile0000000000000000")

        when:
        def provider = new ApiKeyProvider("my-explicit-key", keyFile.toString())

        then:
        provider.source == "property"
        provider.resolvedKey == "my-explicit-key"
    }

    def "generated key has correct format: jaiclaw_ak_ + 32 hex chars"() {
        when:
        String key = ApiKeyProvider.generateKey()

        then:
        key.startsWith("jaiclaw_ak_")
        key.length() == 43
        key.substring(11).matches("[0-9a-f]{32}")
    }

    def "blank explicit key falls through to file or generation"() {
        given:
        Path keyFile = tempDir.resolve("no-exist")

        when:
        def provider = new ApiKeyProvider("   ", keyFile.toString())

        then:
        provider.source == "generated"
        provider.resolvedKey.startsWith("jaiclaw_ak_")
    }

    def "masked key shows only last 8 characters"() {
        when:
        def provider = new ApiKeyProvider("jaiclaw_ak_abcdef1234567890abcdef12", tempDir.resolve("x").toString())

        then:
        provider.maskedKey.endsWith("cdef12")
        !provider.maskedKey.contains("jaiclaw_ak_")
    }

    def "accepts legacy jclaw_ak_ prefix from file"() {
        given:
        Path keyFile = tempDir.resolve("api-key")
        Files.writeString(keyFile, "jclaw_ak_legacykey1234567890abcdef\n")

        when:
        def provider = new ApiKeyProvider(null, keyFile.toString())

        then:
        provider.source == "file"
        provider.resolvedKey == "jclaw_ak_legacykey1234567890abcdef"
    }
}
