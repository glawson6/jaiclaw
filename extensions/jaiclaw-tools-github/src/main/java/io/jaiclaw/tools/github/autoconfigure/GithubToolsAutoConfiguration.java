package io.jaiclaw.tools.github.autoconfigure;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.github.GithubToolsProperties;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import io.jaiclaw.tools.github.client.PatGithubClientProvider;
import io.jaiclaw.tools.github.mcp.GithubToolsMcpProvider;
import io.jaiclaw.tools.github.tools.GithubCommentTool;
import io.jaiclaw.tools.github.tools.GithubCommitCommentCreateTool;
import io.jaiclaw.tools.github.tools.GithubCommitCommentsTool;
import io.jaiclaw.tools.github.tools.GithubIssueGetTool;
import io.jaiclaw.tools.github.tools.GithubPrCommitsTool;
import io.jaiclaw.tools.github.tools.GithubPrDiffTool;
import io.jaiclaw.tools.github.tools.GithubPrFilesTool;
import io.jaiclaw.tools.github.tools.GithubPrGetTool;
import io.jaiclaw.tools.github.tools.GithubPrReviewCommentsTool;
import io.jaiclaw.tools.github.tools.GithubPrReviewsTool;
import io.jaiclaw.tools.github.tools.GithubPrThreadTool;
import io.jaiclaw.tools.github.tools.GithubRepoGetContentTool;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for the JaiClaw GitHub tools. Opt-in via
 * {@code jaiclaw.github.enabled=true}. Registers a {@link GithubClientProvider}
 * (default PAT-based) and 12 {@link ToolCallback} beans. Also registers an
 * MCP provider so the same tools are reachable at {@code /mcp/github}.
 *
 * <p>Registration into {@link ToolRegistry} happens through the framework's
 * {@code ToolBeanDiscovery} — no explicit {@code toolRegistry.register(...)}
 * calls needed here.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
@ConditionalOnClass(name = "org.kohsuke.github.GitHub")
@ConditionalOnProperty(prefix = "jaiclaw.github", name = "enabled", havingValue = "true")
@ConditionalOnBean(ToolRegistry.class)
@EnableConfigurationProperties(GithubToolsProperties.class)
public class GithubToolsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GithubClientProvider githubClientProvider(GithubToolsProperties properties) {
        return new PatGithubClientProvider(properties);
    }

    @Bean
    public ToolCallback githubCommentTool(GithubClientProvider provider) {
        return new GithubCommentTool(provider);
    }

    @Bean
    public ToolCallback githubIssueGetTool(GithubClientProvider provider) {
        return new GithubIssueGetTool(provider);
    }

    @Bean
    public ToolCallback githubPrGetTool(GithubClientProvider provider) {
        return new GithubPrGetTool(provider);
    }

    @Bean
    public ToolCallback githubPrThreadTool(GithubClientProvider provider) {
        return new GithubPrThreadTool(provider);
    }

    @Bean
    public ToolCallback githubPrDiffTool(GithubClientProvider provider) {
        return new GithubPrDiffTool(provider);
    }

    @Bean
    public ToolCallback githubPrFilesTool(GithubClientProvider provider) {
        return new GithubPrFilesTool(provider);
    }

    @Bean
    public ToolCallback githubPrCommitsTool(GithubClientProvider provider) {
        return new GithubPrCommitsTool(provider);
    }

    @Bean
    public ToolCallback githubPrReviewsTool(GithubClientProvider provider) {
        return new GithubPrReviewsTool(provider);
    }

    @Bean
    public ToolCallback githubPrReviewCommentsTool(GithubClientProvider provider) {
        return new GithubPrReviewCommentsTool(provider);
    }

    @Bean
    public ToolCallback githubCommitCommentsTool(GithubClientProvider provider) {
        return new GithubCommitCommentsTool(provider);
    }

    @Bean
    public ToolCallback githubCommitCommentCreateTool(GithubClientProvider provider) {
        return new GithubCommitCommentCreateTool(provider);
    }

    @Bean
    public ToolCallback githubRepoGetContentTool(GithubClientProvider provider) {
        return new GithubRepoGetContentTool(provider);
    }

    @Bean
    public GithubToolsMcpProvider githubToolsMcpProvider(List<ToolCallback> allTools) {
        // Filter to only GitHub tools (identified by the section label).
        List<ToolCallback> githubTools = allTools.stream()
                .filter(t -> "GitHub".equals(t.definition().section()))
                .toList();
        return new GithubToolsMcpProvider(githubTools);
    }
}
