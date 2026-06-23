package io.jaiclaw.secrets.onepassword

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration

/**
 * Tests OnePasswordSecretsProvider against a fake `op` shell script
 * that mirrors the real CLI's exit codes and stdout/stderr patterns.
 * Avoids depending on a real 1Password installation.
 */
class OnePasswordSecretsProviderSpec extends Specification {

    @TempDir
    Path tempDir

    /**
     * Write a fake `op` script at tempDir/op.sh. Behavior controlled by
     * the input `behavior`: "ok" prints "secret-value" and exits 0,
     * "notfound" prints "doesn't exist" to stderr and exits 1, "boom"
     * prints "broken" to stderr and exits 1, "slow" sleeps 5s before
     * printing.
     */
    private Path writeFakeOp(String behavior) {
        Path script = tempDir.resolve("op.sh")
        String body = switch (behavior) {
            case "ok"       -> "echo 'secret-value'"
            case "notfound" -> "echo \"\\\"jaiclaw\\\" isn't an item\" >&2; exit 1"
            case "boom"     -> "echo 'broken: unable to reach the vault' >&2; exit 1"
            case "slow"     -> "sleep 5; echo 'too late'"
            default         -> throw new IllegalArgumentException("unknown behavior: " + behavior)
        }
        Files.writeString(script, "#!/usr/bin/env bash\n${body}\n")
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
        script
    }

    private OnePasswordSecretsProvider provider(Path opBinary) {
        new OnePasswordSecretsProvider(
                new OnePasswordSecretsProvider.Config(
                        opBinary.toString(), "JaiClaw", null, Duration.ofSeconds(2)))
    }

    def "returns value on successful op invocation"() {
        given:
        OnePasswordSecretsProvider p = provider(writeFakeOp("ok"))

        when:
        Optional<String> value = p.get("anthropic/api-key")

        then:
        value.isPresent()
        value.get() == "secret-value"
    }

    def "returns empty when op reports item not found"() {
        given:
        OnePasswordSecretsProvider p = provider(writeFakeOp("notfound"))

        when:
        Optional<String> value = p.get("missing/api-key")

        then:
        value.isEmpty()
    }

    def "throws on non-recoverable op error"() {
        given:
        OnePasswordSecretsProvider p = provider(writeFakeOp("boom"))

        when:
        p.get("anthropic/api-key")

        then:
        OnePasswordSecretsProvider.SecretsProviderException e = thrown()
        e.message.contains("broken")
    }

    def "respects timeout"() {
        given:
        OnePasswordSecretsProvider p = provider(writeFakeOp("slow"))

        when:
        p.get("anthropic/api-key")

        then:
        OnePasswordSecretsProvider.SecretsProviderException e = thrown()
        e.message.contains("timed out")
    }

    def "treats key starting with op:// as full reference, ignores vault"() {
        given:
        Path script = tempDir.resolve("op-echo.sh")
        // Echo the last arg so we can verify it
        Files.writeString(script, "#!/usr/bin/env bash\necho \"\$2\"\n")
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
        OnePasswordSecretsProvider p = new OnePasswordSecretsProvider(
                new OnePasswordSecretsProvider.Config(
                        script.toString(), "JaiClaw", null, Duration.ofSeconds(2)))

        when:
        Optional<String> value = p.get("op://OtherVault/anthropic/api-key")

        then:
        value.isPresent()
        value.get() == "op://OtherVault/anthropic/api-key"  // unchanged, not prefixed with JaiClaw vault
    }

    def "prefixes bare keys with configured vault"() {
        given:
        Path script = tempDir.resolve("op-echo.sh")
        Files.writeString(script, "#!/usr/bin/env bash\necho \"\$2\"\n")
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
        OnePasswordSecretsProvider p = new OnePasswordSecretsProvider(
                new OnePasswordSecretsProvider.Config(
                        script.toString(), "JaiClaw", null, Duration.ofSeconds(2)))

        when:
        Optional<String> value = p.get("anthropic/api-key")

        then:
        value.isPresent()
        value.get() == "op://JaiClaw/anthropic/api-key"
    }

    def "name() returns 'onepassword'"() {
        expect:
        new OnePasswordSecretsProvider(OnePasswordSecretsProvider.Config.of("v")).name() == "onepassword"
    }

    def "getAll returns empty (not supported by op CLI)"() {
        expect:
        new OnePasswordSecretsProvider(OnePasswordSecretsProvider.Config.of("v")).getAll("anything").isEmpty()
    }

    def "Config.of creates default config"() {
        when:
        OnePasswordSecretsProvider.Config c = OnePasswordSecretsProvider.Config.of("MyVault")

        then:
        c.opBinary() == "op"
        c.vault() == "MyVault"
        c.serviceAccountToken() == null
        c.timeout() == Duration.ofSeconds(10)
    }

    def "rejects blank vault"() {
        when:
        new OnePasswordSecretsProvider(
                new OnePasswordSecretsProvider.Config("op", "", null, null))

        then:
        thrown(IllegalArgumentException)
    }
}
