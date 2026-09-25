package io.jaiclaw.compliance.audit;

import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.compliance.encryption.EncryptionBeanPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Wraps every {@link AuditLogger} bean in a {@link HashChainedAuditLogger}, so
 * the audit trail is tamper-evident without the adopter hand-wiring it.
 *
 * <p>Guarded by {@link io.jaiclaw.compliance.JaiClawComplianceAutoConfiguration}
 * so this bean only exists when
 * {@code jaiclaw.compliance.effective.audit-hash-chain=true}.
 *
 * <h2>Why this is the highest-value control in the compliance module</h2>
 * SOC 2 CC7.2 — and the equivalent in every other regime — asks "how do you know
 * these logs were not altered?". {@code HashChainedAuditLogger} answers it: a
 * per-tenant SHA-256 chain over every event, verifiable after the fact via
 * {@code verifyChain(tenantId)}. Before 1.3.0 the class existed but no
 * auto-configuration applied it, so the answer was "an adopter had to write a
 * {@code @Bean} for that", which few did.
 *
 * <h2>Ordering: the chain must be the OUTERMOST decorator</h2>
 * Runs after {@link EncryptionBeanPostProcessor}, giving
 * {@code HashChained(Encrypted(FileAuditLogger))}. So the chain hashes the
 * plaintext event and the encryptor then protects the payload underneath it.
 *
 * <p>The reverse nesting would be subtly broken rather than obviously so. GCM
 * draws a fresh nonce per call, so encrypting the same event twice yields
 * different bytes — a chain computed over ciphertext would not be reproducible,
 * and {@code verifyChain} could not name the offending event even though it
 * would still notice a break.
 *
 * <p>Post-processing is idempotent — an already-chained logger is returned as-is.
 *
 * <p>1.3.0.
 */
public class HashChainedAuditLoggerBeanPostProcessor implements BeanPostProcessor, Ordered {

    /** After {@link EncryptionBeanPostProcessor#ORDER}, so the chain wraps outermost. */
    public static final int ORDER = EncryptionBeanPostProcessor.ORDER + 100;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof HashChainedAuditLogger) return bean;      // idempotent
        if (bean instanceof AuditLogger logger) {
            return new HashChainedAuditLogger(logger);
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
