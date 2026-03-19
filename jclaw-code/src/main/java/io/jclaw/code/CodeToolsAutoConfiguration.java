package io.jclaw.code;

import io.jclaw.tools.ToolRegistry;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers code tools (file_edit, glob, grep)
 * into the JClaw tool registry when present on the classpath.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jclaw.autoconfigure.JClawAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class CodeToolsAutoConfiguration {

    @Bean
    public CodeToolsRegistrar codeToolsRegistrar(ToolRegistry toolRegistry) {
        CodeTools.registerAll(toolRegistry);
        return new CodeToolsRegistrar();
    }

    /**
     * Marker bean to indicate code tools have been registered.
     */
    public static class CodeToolsRegistrar {}
}
