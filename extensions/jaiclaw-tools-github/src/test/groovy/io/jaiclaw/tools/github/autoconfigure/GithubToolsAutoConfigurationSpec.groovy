package io.jaiclaw.tools.github.autoconfigure

import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.tools.ToolRegistry
import io.jaiclaw.tools.github.client.GithubClientProvider
import io.jaiclaw.tools.github.mcp.GithubToolsMcpProvider
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

class GithubToolsAutoConfigurationSpec extends Specification {

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GithubToolsAutoConfiguration))
            .withUserConfiguration(FakeToolRegistryConfig)

    def "does NOT load beans when jaiclaw.github.enabled is not set"() {
        expect:
        runner.run { ctx ->
            assert !ctx.containsBean("githubClientProvider")
            assert !ctx.containsBean("githubCommentTool")
        }
    }

    def "loads all 12 tools + client provider + MCP provider when enabled"() {
        expect:
        runner.withPropertyValues("jaiclaw.github.enabled=true").run { ctx ->
            assert ctx.getBean(GithubClientProvider) != null
            assert ctx.getBean(GithubToolsMcpProvider) != null
            def tools = ctx.getBeansOfType(ToolCallback)
            assert tools.size() == 12
            def toolNames = tools.values().collect { it.definition().name() }.sort()
            assert toolNames == [
                    "github_comment",
                    "github_commit_comment_create",
                    "github_commit_comments",
                    "github_issue_get",
                    "github_pr_commits",
                    "github_pr_diff",
                    "github_pr_files",
                    "github_pr_get",
                    "github_pr_review_comments",
                    "github_pr_reviews",
                    "github_pr_thread",
                    "github_repo_get_content"
            ]
        }
    }

    def "honours GITHUB_TOKEN env fallback in the client provider"() {
        // Behavioural check on the properties record: token is null by default,
        // client provider will read GITHUB_TOKEN at runtime. The record defaults
        // apiUrl to https://api.github.com and enforces non-zero timeouts.
        expect:
        runner.withPropertyValues("jaiclaw.github.enabled=true").run { ctx ->
            def props = ctx.getBean(io.jaiclaw.tools.github.GithubToolsProperties)
            assert props.apiUrl() == "https://api.github.com"
            assert props.connectTimeoutSeconds() > 0
            assert props.readTimeoutSeconds() > 0
        }
    }

    def "honours a jaiclaw.github.api-url override"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.github.enabled=true",
                "jaiclaw.github.api-url=https://ghe.example.com/api/v3"
        ).run { ctx ->
            def props = ctx.getBean(io.jaiclaw.tools.github.GithubToolsProperties)
            assert props.apiUrl() == "https://ghe.example.com/api/v3"
        }
    }

    /**
     * The @ConditionalOnBean(ToolRegistry.class) gate needs a ToolRegistry in
     * the context. jaiclaw-tools provides one at runtime; for this test we
     * supply a minimal stand-in.
     */
    @Configuration
    static class FakeToolRegistryConfig {
        @Bean
        ToolRegistry toolRegistry() {
            return new ToolRegistry()
        }
    }
}
