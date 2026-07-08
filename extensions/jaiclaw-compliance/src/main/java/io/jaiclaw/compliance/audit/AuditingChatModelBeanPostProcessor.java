package io.jaiclaw.compliance.audit;

import io.jaiclaw.audit.AuditLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Wraps every {@link ChatModel} bean the Spring context produces with an
 * {@link AuditingChatModel}. Guarded by {@link
 * io.jaiclaw.compliance.JaiClawComplianceAutoConfiguration}'s conditional so
 * this only exists when {@code jaiclaw.compliance.effective.audit-chat-client=true}.
 *
 * <p>Post-processing is idempotent — an already-wrapped {@code AuditingChatModel}
 * is returned as-is. This tolerates the (rare) case where the bean graph
 * contains two paths to the same underlying model.
 */
public class AuditingChatModelBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<AuditLogger> auditLoggerProvider;

    public AuditingChatModelBeanPostProcessor(ObjectProvider<AuditLogger> auditLoggerProvider) {
        this.auditLoggerProvider = auditLoggerProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof AuditingChatModel) return bean;      // idempotent
        if (bean instanceof ChatModel model) {
            AuditLogger logger = auditLoggerProvider.getIfAvailable();
            if (logger == null) return bean;                     // no audit → no wrap
            return new AuditingChatModel(model, logger);
        }
        return bean;
    }
}
