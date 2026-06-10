package io.jaiclaw.promptanalyzer;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.Map;

/**
 * Auto-configuration that registers a {@code prompt_analyze} tool in the
 * {@link ToolRegistry} when embedded in a JaiClaw runtime.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class PromptAnalyzerAutoConfiguration {

    @Bean
    public Object promptAnalyzerToolRegistrar(ToolRegistry registry) {
        registry.register(new PromptAnalyzeTool());
        return new Object();
    }

    static class PromptAnalyzeTool implements ToolCallback {

        private static final ToolDefinition DEFINITION = ToolDefinition.builder()
                .name("prompt_analyze")
                .description("Analyze a JaiClaw project directory and estimate input token usage")
                .section("analysis")
                .inputSchema("""
                        {"type":"object","properties":{"path":{"type":"string","description":"Path to the JaiClaw project directory"}},"required":["path"]}""")
                .build();

        private final ProjectScanner scanner = new ProjectScanner();

        @Override
        public ToolDefinition definition() {
            return DEFINITION;
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            Object pathObj = parameters.get("path");
            if (pathObj == null) {
                return new ToolResult.Error("Missing required parameter: path", null);
            }
            try {
                AnalysisReport report = scanner.analyze(Path.of(pathObj.toString()).toAbsolutePath());
                return new ToolResult.Success(report.format());
            } catch (Exception e) {
                return new ToolResult.Error(e.getMessage(), e);
            }
        }
    }
}
