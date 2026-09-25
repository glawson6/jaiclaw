package io.jaiclaw.compliance.encryption;

import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.audit.TranscriptStore;
import io.jaiclaw.core.encryption.FieldEncryptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Wraps every {@link AuditLogger} and {@link TranscriptStore} bean in its
 * encrypting decorator, so payloads are encrypted at rest without the adopter
 * hand-wiring the chain.
 *
 * <p>Guarded by {@link io.jaiclaw.compliance.JaiClawComplianceAutoConfiguration}
 * so this bean only exists when
 * {@code jaiclaw.compliance.effective.encrypt-at-rest=true}. Before 1.3.0 the
 * two decorators existed but nothing applied them — encryption at rest was
 * reachable only by an adopter writing their own {@code @Bean}.
 *
 * <p>Post-processing is idempotent: an already-wrapped bean is returned as-is,
 * which tolerates a bean graph containing two paths to the same store.
 *
 * <h2>Ordering against the audit hash chain</h2>
 * When both {@code encrypt-at-rest} and {@code audit-hash-chain} are on, the
 * chain must sit <strong>outside</strong> the encryptor — hash the plaintext
 * event, then encrypt. Chaining over ciphertext would still detect tampering,
 * but {@code verifyChain} could not report which event broke, and re-encrypting
 * the same event yields different bytes (fresh GCM nonce per call), so a chain
 * over ciphertext would not be reproducible. {@code HashChainedAuditLoggerBeanPostProcessor}
 * therefore runs at a later order than this one; see the ordering constants on
 * both classes.
 *
 * <p>1.3.0.
 */
public class EncryptionBeanPostProcessor implements BeanPostProcessor, Ordered {

    /**
     * Runs before the hash-chain post-processor so the chain ends up as the
     * outermost decorator. Lower value = earlier = more deeply nested.
     */
    public static final int ORDER = 100;

    private final ObjectProvider<FieldEncryptor> encryptorProvider;

    public EncryptionBeanPostProcessor(ObjectProvider<FieldEncryptor> encryptorProvider) {
        this.encryptorProvider = encryptorProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // Decide from the bean type ALONE before touching the ObjectProvider.
        //
        // This ordering is load-bearing, not stylistic. A BeanPostProcessor sees
        // every bean in the context — including the FieldEncryptor this one
        // depends on. Resolving the provider first meant getIfAvailable() ran
        // while `fieldEncryptor` was itself mid-creation, re-entering its own
        // factory method: BeanCurrentlyInCreationException. Narrowing first means
        // the provider is only consulted for beans we actually intend to wrap,
        // and the encryptor is never one of them.
        boolean wrappable = (bean instanceof AuditLogger && !(bean instanceof EncryptedAuditLogger))
                || (bean instanceof TranscriptStore && !(bean instanceof EncryptedTranscriptStore));
        if (!wrappable) {
            return bean;
        }

        FieldEncryptor encryptor = encryptorProvider.getIfAvailable();
        if (encryptor == null) {
            // Unreachable through the auto-configuration, which fails startup
            // when encryption is enabled without a resolvable key. Returning the
            // bean unwrapped rather than throwing keeps a hand-wired context
            // working.
            return bean;
        }

        if (bean instanceof AuditLogger logger) {
            return new EncryptedAuditLogger(logger, encryptor);
        }
        return new EncryptedTranscriptStore((TranscriptStore) bean, encryptor);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
