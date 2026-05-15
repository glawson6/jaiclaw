package io.jaiclaw.scaffold.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ScaffoldMcpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldMcpAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ScaffoldMcpToolProvider scaffoldMcpToolProvider(ObjectMapper objectMapper) {
        log.info("Registering project-scaffolder MCP tool provider (scaffold_project, validate_manifest, list_options)");
        return new ScaffoldMcpToolProvider(objectMapper);
    }
}
