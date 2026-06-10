package io.jaiclaw.documents.tools;

import io.jaiclaw.tools.ToolRegistry;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class DocumentToolsAutoConfiguration {

    @Bean
    public DocumentToolsRegistrar documentToolsRegistrar(ToolRegistry toolRegistry) {
        DocumentTools.registerAll(toolRegistry);
        return new DocumentToolsRegistrar();
    }

    public static class DocumentToolsRegistrar {}
}
