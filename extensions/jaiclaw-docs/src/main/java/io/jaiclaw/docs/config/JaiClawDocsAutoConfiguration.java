package io.jaiclaw.docs.config;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.docs.DocsMcpResourceProvider;
import io.jaiclaw.docs.DocsMcpToolProvider;
import io.jaiclaw.docs.DocsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawGatewayAutoConfiguration")
@ConditionalOnProperty(name = "jaiclaw.docs.enabled", havingValue = "true")
public class JaiClawDocsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawDocsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public DocsRepository docsRepository() {
        log.info("Initializing JaiClaw docs repository from classpath");
        return new DocsRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public DocsMcpResourceProvider docsMcpResourceProvider(DocsRepository repository) {
        log.info("Registering docs MCP resource provider");
        return new DocsMcpResourceProvider(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public DocsMcpToolProvider docsMcpToolProvider(DocsRepository repository, ObjectMapper objectMapper) {
        log.info("Registering docs MCP tool provider (search_docs)");
        return new DocsMcpToolProvider(repository, objectMapper);
    }
}
