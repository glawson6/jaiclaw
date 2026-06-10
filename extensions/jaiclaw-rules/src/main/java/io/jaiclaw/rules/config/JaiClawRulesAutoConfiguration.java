package io.jaiclaw.rules.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.rules.engine.config.DroolsConfig;
import io.jaiclaw.rules.engine.config.DroolsProperties;
import io.jaiclaw.rules.engine.loader.RuleLoaderFactory;
import io.jaiclaw.rules.engine.service.RuleExecutionService;
import io.jaiclaw.rules.engine.service.impl.DroolsRuleExecutionService;
import io.jaiclaw.rules.mcp.RulesMcpToolProvider;
import io.jaiclaw.rules.tool.RulesTools;
import io.jaiclaw.tools.ToolRegistry;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
@ConditionalOnProperty(name = "jaiclaw.rules.enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(DroolsProperties.class)
public class JaiClawRulesAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawRulesAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RuleLoaderFactory ruleLoaderFactory() {
        return new RuleLoaderFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public DroolsConfig droolsConfig(DroolsProperties droolsProperties, RuleLoaderFactory ruleLoaderFactory) {
        return new DroolsConfig(droolsProperties, ruleLoaderFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public KieContainer kieContainer(DroolsConfig droolsConfig) {
        return droolsConfig.kieContainer();
    }

    @Bean
    @ConditionalOnMissingBean
    public StatelessKieSession kieSession(DroolsConfig droolsConfig, KieContainer kieContainer) {
        return droolsConfig.kieSession(kieContainer);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleExecutionService ruleExecutionService(StatelessKieSession kieSession) {
        return new DroolsRuleExecutionService(kieSession);
    }

    @Bean
    @ConditionalOnBean(ToolRegistry.class)
    public RulesToolsRegistrar rulesToolsRegistrar(ToolRegistry toolRegistry,
                                                    RuleExecutionService ruleExecutionService) {
        log.info("Registering Rules tools into ToolRegistry");
        RulesTools.registerAll(toolRegistry, ruleExecutionService);
        return new RulesToolsRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean
    public RulesMcpToolProvider rulesMcpToolProvider(RuleExecutionService ruleExecutionService) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new RulesMcpToolProvider(ruleExecutionService, objectMapper);
    }

    public static class RulesToolsRegistrar {}
}
