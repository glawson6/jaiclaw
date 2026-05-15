package io.jaiclaw.code;

import io.jaiclaw.tools.ToolRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers code tools (file_edit, glob, grep)
 * into the JaiClaw tool registry when present on the classpath.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class CodeToolsAutoConfiguration {

    @Bean
    public CodeToolsRegistrar codeToolsRegistrar(
            ToolRegistry toolRegistry,
            @Value("${jaiclaw.tools.code.workspace-boundary:true}") boolean workspaceBoundary) {
        CodeTools.registerAll(toolRegistry, workspaceBoundary);
        return new CodeToolsRegistrar();
    }

    /**
     * Marker bean to indicate code tools have been registered.
     */
    public static class CodeToolsRegistrar {}
}
