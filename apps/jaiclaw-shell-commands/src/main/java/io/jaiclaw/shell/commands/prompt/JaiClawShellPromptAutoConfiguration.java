package io.jaiclaw.shell.commands.prompt;

import io.jaiclaw.config.JaiClawProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.shell.jline.PromptProvider;

/**
 * Registers a {@link JaiClawPromptProvider} so the REPL prompt obeys
 * {@code jaiclaw.shell.prompt.format}. Activates only when Spring Shell's
 * {@link PromptProvider} is on the classpath, so non-shell apps (e.g. the
 * gateway) skip this entirely. Registered via the module's own
 * {@code AutoConfiguration.imports} file rather than the central starter's,
 * because the central starter does not depend on jaiclaw-shell-commands.
 */
@AutoConfiguration
@ConditionalOnClass(PromptProvider.class)
@EnableConfigurationProperties(PromptProperties.class)
public class JaiClawShellPromptAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PromptProvider jaiClawPromptProvider(PromptProperties properties,
                                                 ObjectProvider<JaiClawProperties> jaiClawProperties,
                                                 Environment environment) {
        return new JaiClawPromptProvider(properties, jaiClawProperties, environment);
    }
}
