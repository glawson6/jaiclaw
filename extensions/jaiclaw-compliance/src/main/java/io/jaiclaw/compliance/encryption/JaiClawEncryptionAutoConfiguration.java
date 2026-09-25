package io.jaiclaw.compliance.encryption;

import io.jaiclaw.audit.TranscriptStore;
import io.jaiclaw.compliance.audit.HashChainedAuditLoggerBeanPostProcessor;
import io.jaiclaw.core.encryption.FieldEncryptor;
import io.jaiclaw.core.secrets.SecretsResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Encryption at rest and tamper-evident audit logging.
 *
 * <h2>Why this is a separate auto-configuration</h2>
 * A {@code BeanPostProcessor} must be instantiated before any regular bean, so
 * if one is declared in the same {@code @Configuration} class as a bean it
 * depends on, Spring has to instantiate that class early — and
 * {@code BeanCurrentlyInCreationException} follows. Keeping the
 * {@link FieldEncryptor} and the post-processors in <em>separate</em> classes
 * breaks the cycle: the BPP holders carry no other beans, so instantiating them
 * early costs nothing.
 *
 * <p>(The pre-existing {@code AuditingChatModelBeanPostProcessor} sits in the
 * main compliance auto-configuration without trouble only because its
 * dependency — {@code AuditLogger} — is declared elsewhere.)
 *
 * <p>1.3.0.
 */
@AutoConfiguration
@ConditionalOnClass(TranscriptStore.class)
public class JaiClawEncryptionAutoConfiguration {

    /**
     * The 32-byte AES key backing encryption at rest.
     *
     * <p><strong>Fails startup when no key is resolvable.</strong> That is
     * deliberate: {@code encrypt-at-rest=true} is an operator asserting that
     * data is encrypted, and silently persisting plaintext while they believe
     * otherwise is the worst available outcome. See {@link EncryptionKeyResolver}
     * for accepted encodings and why a passphrase is refused.
     */
    @Bean
    @ConditionalOnMissingBean(FieldEncryptor.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.encrypt-at-rest", havingValue = "true")
    public FieldEncryptor fieldEncryptor(Environment environment,
                                         ObjectProvider<SecretsResolver> secretsResolver) {
        byte[] key = EncryptionKeyResolver.resolve(
                environment.getProperty(EncryptionKeyResolver.KEY_PROPERTY),
                secretsResolver.getIfAvailable());
        return new AesGcmFieldEncryptor(key);
    }

    /**
     * Holder for the encrypting post-processor. Separate class, no other beans —
     * see the class-level note on why.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.encrypt-at-rest", havingValue = "true")
    static class EncryptionPostProcessorConfiguration {

        @Bean
        @ConditionalOnMissingBean(EncryptionBeanPostProcessor.class)
        static EncryptionBeanPostProcessor encryptionBeanPostProcessor(
                ObjectProvider<FieldEncryptor> encryptorProvider) {
            return new EncryptionBeanPostProcessor(encryptorProvider);
        }
    }

    /**
     * Holder for the hash-chain post-processor. Takes no dependencies at all, so
     * it can never participate in a cycle.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.audit-hash-chain", havingValue = "true")
    static class HashChainPostProcessorConfiguration {

        @Bean
        @ConditionalOnMissingBean(HashChainedAuditLoggerBeanPostProcessor.class)
        static HashChainedAuditLoggerBeanPostProcessor hashChainedAuditLoggerBeanPostProcessor() {
            return new HashChainedAuditLoggerBeanPostProcessor();
        }
    }
}
