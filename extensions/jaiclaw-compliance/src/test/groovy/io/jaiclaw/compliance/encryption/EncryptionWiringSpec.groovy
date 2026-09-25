package io.jaiclaw.compliance.encryption

import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.audit.InMemoryAuditLogger
import io.jaiclaw.compliance.audit.HashChainedAuditLogger
import io.jaiclaw.core.encryption.FieldEncryptor
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/**
 * Bean-materialisation tests for the encryption-at-rest wiring.
 *
 * <p>The first ApplicationContextRunner test in this module — the conditional
 * wiring in JaiClawComplianceAutoConfiguration was previously unverified by any
 * context test, so "does this profile actually produce these beans?" had no
 * answer outside manual inspection.
 */
class EncryptionWiringSpec extends Specification {

    static final String KEY_B64 = Base64.encoder.encodeToString(AesGcmFieldEncryptor.generateKey())

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JaiClawEncryptionAutoConfiguration))
            .withUserConfiguration(BackingAuditLoggerConfig)

    def "no encryption beans by default — the guarantee every non-compliance deployment relies on"() {
        expect:
        runner.run { ctx ->
            assert !ctx.containsBean("fieldEncryptor")
            assert ctx.getBeanNamesForType(FieldEncryptor).length == 0
            assert !(ctx.getBean(AuditLogger) instanceof EncryptedAuditLogger)
        }
    }

    def "encrypt-at-rest wires a FieldEncryptor and wraps the AuditLogger"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.compliance.effective.encrypt-at-rest=true",
                "jaiclaw.compliance.encryption.key=" + KEY_B64
        ).run { ctx ->
            assert ctx.getBean(FieldEncryptor) instanceof AesGcmFieldEncryptor
            assert ctx.getBean(AuditLogger) instanceof EncryptedAuditLogger
        }
    }

    def "encrypt-at-rest without a key ABORTS startup rather than running plaintext"() {
        expect:
        runner.withPropertyValues("jaiclaw.compliance.effective.encrypt-at-rest=true")
                .run { ctx ->
                    assert ctx.startupFailure != null
                    assert ctx.startupFailure.message.contains("encryption.key")
                            || rootCauseMessage(ctx.startupFailure).contains("encryption.key")
                }
    }

    def "audit-hash-chain wraps the AuditLogger"() {
        expect:
        runner.withPropertyValues("jaiclaw.compliance.effective.audit-hash-chain=true")
                .run { ctx ->
                    assert ctx.getBean(AuditLogger) instanceof HashChainedAuditLogger
                }
    }

    def "both flags: the hash chain must be the OUTERMOST decorator"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.compliance.effective.encrypt-at-rest=true",
                "jaiclaw.compliance.effective.audit-hash-chain=true",
                "jaiclaw.compliance.encryption.key=" + KEY_B64
        ).run { ctx ->
            def logger = ctx.getBean(AuditLogger)

            // The chain hashes plaintext, then the encryptor protects the payload
            // underneath. Reversed, the chain would cover ciphertext — still
            // tamper-detecting, but not reproducible (fresh GCM nonce per call)
            // and unable to name the offending event.
            assert logger instanceof HashChainedAuditLogger
        }
    }

    def "a resolved encryptor actually round-trips"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.compliance.effective.encrypt-at-rest=true",
                "jaiclaw.compliance.encryption.key=" + KEY_B64
        ).run { ctx ->
            def enc = ctx.getBean(FieldEncryptor)
            def ct = enc.encrypt("audit payload")
            assert ct != "audit payload"
            assert enc.decrypt(ct) == "audit payload"
        }
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable c = t
        while (c.cause != null) c = c.cause
        return c.message ?: ""
    }

    @Configuration(proxyBeanMethods = false)
    static class BackingAuditLoggerConfig {
        @Bean
        AuditLogger auditLogger() {
            return new InMemoryAuditLogger()
        }
    }
}
