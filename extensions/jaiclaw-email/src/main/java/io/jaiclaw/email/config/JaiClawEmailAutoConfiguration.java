package io.jaiclaw.email.config;

import io.jaiclaw.email.mcp.EmailMcpToolProvider;
import io.jaiclaw.email.provider.EmailSender;
import io.jaiclaw.email.provider.Smtp2goEmailSender;
import io.jaiclaw.email.tool.EmailTools;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnProperty(name = "jaiclaw.email.enabled", havingValue = "true", matchIfMissing = false)
public class JaiClawEmailAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawEmailAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EmailProperties emailProperties(Environment environment) {
        return Binder.get(environment)
                .bind("jaiclaw.email", EmailProperties.class)
                .orElseGet(() -> new EmailProperties(true, "smtp2go", null, null, null));
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    @ConditionalOnProperty(name = "jaiclaw.email.provider", havingValue = "smtp2go", matchIfMissing = true)
    public Smtp2goEmailSender smtp2goEmailSender(EmailProperties emailProperties) {
        log.info("Configuring SMTP2GO email sender");
        return new Smtp2goEmailSender(emailProperties);
    }

    @Bean
    @ConditionalOnBean(ToolRegistry.class)
    public EmailToolsRegistrar emailToolsRegistrar(ToolRegistry toolRegistry,
                                                     EmailSender emailSender,
                                                     EmailProperties emailProperties) {
        log.info("Registering Email tools into ToolRegistry");
        EmailTools.registerAll(toolRegistry, emailSender, emailProperties);
        return new EmailToolsRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailMcpToolProvider emailMcpToolProvider(EmailSender emailSender,
                                                      EmailProperties emailProperties) {
        return new EmailMcpToolProvider(emailSender, emailProperties);
    }

    public static class EmailToolsRegistrar {}
}
