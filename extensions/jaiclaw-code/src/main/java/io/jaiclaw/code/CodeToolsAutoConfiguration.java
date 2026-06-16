package io.jaiclaw.code;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that contributes code tools (file_edit, glob, grep) as
 * Spring beans. Registration into {@link ToolRegistry} is handled by
 * {@code ToolBeanDiscovery} — no explicit {@code toolRegistry.register(...)}
 * call needed.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class CodeToolsAutoConfiguration {

    @Bean
    public ToolCallback fileEditTool(
            @Value("${jaiclaw.tools.code.workspace-boundary:true}") boolean workspaceBoundary) {
        return new FileEditTool(workspaceBoundary);
    }

    @Bean
    public ToolCallback globTool(
            @Value("${jaiclaw.tools.code.workspace-boundary:true}") boolean workspaceBoundary) {
        return new GlobTool(workspaceBoundary);
    }

    @Bean
    public ToolCallback grepTool(
            @Value("${jaiclaw.tools.code.workspace-boundary:true}") boolean workspaceBoundary) {
        return new GrepTool(workspaceBoundary);
    }
}
