package io.jaiclaw.compliance;

import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.audit.RetentionEnforcementService;
import io.jaiclaw.audit.TranscriptStore;
import io.jaiclaw.compliance.audit.AuditingChatModelBeanPostProcessor;
import io.jaiclaw.compliance.audit.BaaWarningChatModelDecorator;
import io.jaiclaw.config.ModelsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-config for the compliance module. All beans are gated on effective
 * flags written by {@link ComplianceEnvironmentPostProcessor}. Nothing
 * loads when the operator sets {@code jaiclaw.compliance.profile=none}
 * and doesn't override individual flags.
 */
@AutoConfiguration
@ConditionalOnClass(TranscriptStore.class)
public class JaiClawComplianceAutoConfiguration {

    /**
     * RetentionEnforcementService — only exists when retention enforcement
     * is on. Uses the audit + transcript beans registered elsewhere.
     */
    @Bean
    @ConditionalOnMissingBean(RetentionEnforcementService.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.retention-enforcement", havingValue = "true")
    public RetentionEnforcementService retentionEnforcementService(
            ObjectProvider<TranscriptStore> transcriptStores,
            ObjectProvider<AuditLogger> auditLoggers) {
        return new RetentionEnforcementService(
                transcriptStores.stream().toList(),
                auditLoggers.stream().toList());
    }

    /**
     * BaaWarningChatModelDecorator — only exists when BAA warnings are on.
     * The decorator is a passive check invoked at ChatModel creation.
     */
    @Bean
    @ConditionalOnMissingBean(BaaWarningChatModelDecorator.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.baa-warnings", havingValue = "true")
    public BaaWarningChatModelDecorator baaWarningChatModelDecorator(
            ObjectProvider<ModelsProperties> modelsPropertiesProvider) {
        return new BaaWarningChatModelDecorator(
                modelsPropertiesProvider.getIfAvailable(() -> new ModelsProperties(java.util.Map.of())));
    }

    /**
     * T1-3: wrap every {@code ChatModel} bean with {@code AuditingChatModel}
     * so every LLM call emits a structured {@code model.inference.request}
     * audit event. Only exists when {@code jaiclaw.compliance.effective.audit-chat-client=true}.
     */
    @Bean
    @ConditionalOnMissingBean(AuditingChatModelBeanPostProcessor.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.audit-chat-client", havingValue = "true")
    public AuditingChatModelBeanPostProcessor auditingChatModelBeanPostProcessor(
            ObjectProvider<AuditLogger> auditLoggerProvider) {
        return new AuditingChatModelBeanPostProcessor(auditLoggerProvider);
    }
}

