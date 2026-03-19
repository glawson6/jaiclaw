package io.jclaw.security

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
        provider.resolvedKey.startsWith("jclaw_ak_")
        provider.resolvedKey.length() == 41
        Files.readString(keyFile).trim() == provider.resolvedKey
    }

    def "reads existing key from file"() {
        given:
        Path keyFile = tempDir.resolve("api-key")
        Files.writeString(keyFile, "jclaw_ak_existingkey1234567890ab\n")

        when:
        def provider = new ApiKeyProvider(null, keyFile.toString())

        then:
        provider.source == "file"
        provider.resolvedKey == "jclaw_ak_existingkey1234567890ab"
    }

    def "explicit key overrides file"() {
        given:
        Path keyFile = tempDir.resolve("api-key")
        Files.writeString(keyFile, "jclaw_ak_fromfile00000000000000")

        when:
        def provider = new ApiKeyProvider("my-explicit-key", keyFile.toString())

        then:
        provider.source == "property"
        provider.resolvedKey == "my-explicit-key"
    }

    def "generated key has correct format: jclaw_ak_ + 32 hex chars"() {
        when:
        String key = ApiKeyProvider.generateKey()

        then:
        key.startsWith("jclaw_ak_")
        key.length() == 41
        key.substring(9).matches("[0-9a-f]{32}")
    }

    def "blank explicit key falls through to file or generation"() {
        given:
        Path keyFile = tempDir.resolve("no-exist")

        when:
        def provider = new ApiKeyProvider("   ", keyFile.toString())

        then:
        provider.source == "generated"
        provider.resolvedKey.startsWith("jclaw_ak_")
    }

    def "masked key shows only last 8 characters"() {
        when:
        def provider = new ApiKeyProvider("jclaw_ak_abcdef1234567890abcdef12", tempDir.resolve("x").toString())

        then:
        provider.maskedKey.endsWith("bcdef12")
        !provider.maskedKey.contains("jclaw_ak_")
    }
}
