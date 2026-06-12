package io.jaiclaw.tools.builtin.mcp;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers an {@link AsciiRenderMcpToolProvider} bean so the renderer
 * is also reachable over {@code /mcp/ascii-render} for external MCP
 * clients (other LLMs, Claude Desktop, downstream services).
 *
 * <p>Gated on {@code io.jaiclaw.gateway.mcp.McpServerRegistry} being on
 * classpath — when {@code jaiclaw-gateway} is not present, neither this
 * class nor the bean it produces affects the application.
 *
 * <p>Disable explicitly with
 * {@code jaiclaw.tools.ascii-render.mcp.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.jaiclaw.gateway.mcp.McpServerRegistry")
@ConditionalOnProperty(
        prefix = "jaiclaw.tools.ascii-render.mcp",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AsciiRenderMcpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AsciiRenderMcpToolProvider asciiRenderMcpToolProvider() {
        return new AsciiRenderMcpToolProvider();
    }
}
