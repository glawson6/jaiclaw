package io.jaiclaw.compliance;

import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.audit.RetentionEnforcementService;
import io.jaiclaw.audit.TranscriptStore;
import io.jaiclaw.compliance.audit.AuditingChatModelBeanPostProcessor;
import io.jaiclaw.compliance.audit.BaaWarningChatModelDecorator;
import io.jaiclaw.compliance.gdpr.AggregateDataSubjectErasureSpi;
import io.jaiclaw.compliance.gdpr.AggregateDataSubjectExportService;
import io.jaiclaw.compliance.gdpr.AuditBasedRopaGenerator;
import io.jaiclaw.compliance.gdpr.DefaultPrivacyNoticeService;
import io.jaiclaw.compliance.gdpr.DefaultProcessorMetadataExporter;
import io.jaiclaw.compliance.gdpr.GdprAuthzExpressions;
import io.jaiclaw.compliance.gdpr.GdprAuthzProperties;
import io.jaiclaw.compliance.gdpr.InMemoryConsentManager;
import io.jaiclaw.compliance.gdpr.InMemoryObjectionService;
import io.jaiclaw.compliance.gdpr.RegexPromptRedactor;
import io.jaiclaw.config.ModelsProperties;
import io.jaiclaw.core.gdpr.ConsentManager;
import io.jaiclaw.core.gdpr.DataSubjectErasureSpi;
import io.jaiclaw.core.gdpr.DataSubjectExportService;
import io.jaiclaw.core.gdpr.ObjectionService;
import io.jaiclaw.core.gdpr.PrivacyNoticeService;
import io.jaiclaw.core.gdpr.ProcessorMetadataExporter;
import io.jaiclaw.core.gdpr.PromptRedactor;
import io.jaiclaw.core.gdpr.RopaGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-config for the compliance module. All beans are gated on effective
 * flags written by {@link ComplianceEnvironmentPostProcessor}. Nothing
 * loads when the operator sets {@code jaiclaw.compliance.profile=none}
 * and doesn't override individual flags.
 */
@AutoConfiguration
@ConditionalOnClass(TranscriptStore.class)
@EnableConfigurationProperties(GdprAuthzProperties.class)
public class JaiClawComplianceAutoConfiguration {

    /**
     * Bean referenced from {@code @PreAuthorize} SpEL on
     * {@link io.jaiclaw.compliance.gdpr.GdprController} Article 15 / 17
     * endpoints. Always registered — the {@code @PreAuthorize} annotations
     * on the controller are inert when Spring Security is absent from the
     * classpath, so the bean can safely exist even in non-Security
     * deployments.
     */
    @Bean(name = "gdprAuthzExpressions")
    @ConditionalOnMissingBean(GdprAuthzExpressions.class)
    public GdprAuthzExpressions gdprAuthzExpressions(GdprAuthzProperties properties) {
        return new GdprAuthzExpressions(properties.roles());
    }

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
     * Federal compliance (2026-08): FedRAMP-authorized-provider warning
     * decorator. Only exists when FedRAMP warnings are on
     * ({@code jaiclaw.compliance.effective.fedramp-warnings=true}).
     * Auto-enabled by the {@code fedramp-moderate} profile; opt-in
     * individually via {@code jaiclaw.compliance.fedramp-warnings=true}.
     */
    @Bean
    @ConditionalOnMissingBean(io.jaiclaw.compliance.fedramp.FedRampWarningChatModelDecorator.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.fedramp-warnings", havingValue = "true")
    public io.jaiclaw.compliance.fedramp.FedRampWarningChatModelDecorator fedrampWarningChatModelDecorator(
            ObjectProvider<ModelsProperties> modelsPropertiesProvider) {
        return new io.jaiclaw.compliance.fedramp.FedRampWarningChatModelDecorator(
                modelsPropertiesProvider.getIfAvailable(() -> new ModelsProperties(java.util.Map.of())));
    }

    /**
     * Federal compliance (2026-08): CUI-authorized-provider warning
     * decorator (CMMC L2 / DFARS 252.204-7012). Only exists when CUI
     * warnings are on ({@code jaiclaw.compliance.effective.cui-warnings=true}).
     * Auto-enabled by the {@code cmmc-l2} profile; opt-in individually
     * via {@code jaiclaw.compliance.cui-warnings=true}.
     */
    @Bean
    @ConditionalOnMissingBean(io.jaiclaw.compliance.cui.CuiWarningChatModelDecorator.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.cui-warnings", havingValue = "true")
    public io.jaiclaw.compliance.cui.CuiWarningChatModelDecorator cuiWarningChatModelDecorator(
            ObjectProvider<ModelsProperties> modelsPropertiesProvider) {
        return new io.jaiclaw.compliance.cui.CuiWarningChatModelDecorator(
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

    /**
     * T2-1: aggregate {@link DataSubjectErasureSpi} fans out to every registered
     * {@link TranscriptStore} + {@link AuditLogger}. Wired whenever any compliance
     * profile is active (retention-enforcement is a reasonable proxy).
     */
    @Bean
    @ConditionalOnMissingBean(DataSubjectErasureSpi.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.retention-enforcement", havingValue = "true")
    public DataSubjectErasureSpi dataSubjectErasureSpi(
            ObjectProvider<TranscriptStore> transcriptStores,
            ObjectProvider<AuditLogger> auditLoggers) {
        return new AggregateDataSubjectErasureSpi(
                transcriptStores.stream().toList(),
                auditLoggers.stream().toList());
    }

    /**
     * T2-2: aggregate {@link DataSubjectExportService} — gathers subject data
     * across registered stores. Wired under the same gate as erasure.
     */
    @Bean
    @ConditionalOnMissingBean(DataSubjectExportService.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.retention-enforcement", havingValue = "true")
    public DataSubjectExportService dataSubjectExportService(
            ObjectProvider<TranscriptStore> transcriptStores,
            ObjectProvider<AuditLogger> auditLoggers) {
        return new AggregateDataSubjectExportService(
                transcriptStores.stream().toList(),
                auditLoggers.stream().toList());
    }

    /**
     * T2-3: regex-based {@link PromptRedactor}. Wired under
     * {@code jaiclaw.compliance.effective.prompt-redaction}.
     */
    @Bean
    @ConditionalOnMissingBean(PromptRedactor.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.prompt-redaction", havingValue = "true")
    public PromptRedactor promptRedactor() {
        return new RegexPromptRedactor();
    }

    /**
     * T2-5: in-memory {@link ConsentManager}. Adopters should replace with a
     * durable impl for production. Wired whenever compliance is on.
     */
    @Bean
    @ConditionalOnMissingBean(ConsentManager.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.retention-enforcement", havingValue = "true")
    public ConsentManager consentManager(ObjectProvider<AuditLogger> auditLoggers) {
        return new InMemoryConsentManager(auditLoggers.stream().toList());
    }

    /**
     * T2-7: default {@link PrivacyNoticeService}. Notice text sourced from
     * {@code jaiclaw.compliance.privacy-notice-text}; empty by default so
     * adopters must supply their own copy.
     */
    @Bean
    @ConditionalOnMissingBean(PrivacyNoticeService.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.retention-enforcement", havingValue = "true")
    public PrivacyNoticeService privacyNoticeService(
            org.springframework.core.env.Environment env,
            ObjectProvider<AuditLogger> auditLoggers) {
        String text = env.getProperty("jaiclaw.compliance.privacy-notice-text", "");
        return new DefaultPrivacyNoticeService(text, auditLoggers.stream().toList());
    }

    /**
     * T3-1: {@link RopaGenerator} rebuilt from the audit trail. Wired under
     * the same gate as the other GDPR-facing beans.
     */
    @Bean
    @ConditionalOnMissingBean(RopaGenerator.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.retention-enforcement", havingValue = "true")
    public RopaGenerator ropaGenerator(ObjectProvider<AuditLogger> auditLoggers) {
        return new AuditBasedRopaGenerator(auditLoggers.stream().toList());
    }

    /**
     * T3-2: {@link ObjectionService} — sibling of ConsentManager for Art. 21
     * objections. In-memory reference impl; production adopters override.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectionService.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.retention-enforcement", havingValue = "true")
    public ObjectionService objectionService(ObjectProvider<AuditLogger> auditLoggers) {
        return new InMemoryObjectionService(auditLoggers.stream().toList());
    }

    /**
     * T3-4: {@link ProcessorMetadataExporter} for DPA generation. Processor
     * name + contact email sourced from
     * {@code jaiclaw.compliance.processor-name} +
     * {@code jaiclaw.compliance.processor-contact-email}.
     */
    @Bean
    @ConditionalOnMissingBean(ProcessorMetadataExporter.class)
    @ConditionalOnProperty(name = "jaiclaw.compliance.effective.retention-enforcement", havingValue = "true")
    public ProcessorMetadataExporter processorMetadataExporter(
            org.springframework.core.env.Environment env,
            ObjectProvider<ModelsProperties> modelsPropertiesProvider) {
        String name = env.getProperty("jaiclaw.compliance.processor-name", "JaiClaw deployment");
        String email = env.getProperty("jaiclaw.compliance.processor-contact-email", "");
        ModelsProperties props = modelsPropertiesProvider.getIfAvailable(
                () -> new ModelsProperties(java.util.Map.of()));
        return new DefaultProcessorMetadataExporter(name, email, props, java.util.List.of());
    }
}

