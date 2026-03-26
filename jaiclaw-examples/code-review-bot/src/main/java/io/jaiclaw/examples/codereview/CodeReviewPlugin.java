package io.jaiclaw.examples.codereview;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Plugin that registers a get_diff tool for fetching code diffs from GitHub PRs.
 */
@Component
public class CodeReviewPlugin implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewPlugin.class);

    private final GitHubClientProvider gitHubClientProvider;

    public CodeReviewPlugin(GitHubClientProvider gitHubClientProvider) {
        this.gitHubClientProvider = gitHubClientProvider;
    }

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "code-review-plugin",
                "Code Review Plugin",
                "Provides tools for fetching and analyzing code diffs from GitHub",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new GetDiffTool(gitHubClientProvider));
    }

    /**
     * Tool that fetches a real code diff from a GitHub pull request.
     */
    static class GetDiffTool implements ToolCallback {

        private final GitHubClientProvider gitHubClientProvider;

        GetDiffTool(GitHubClientProvider gitHubClientProvider) {
            this.gitHubClientProvider = gitHubClientProvider;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "get_diff",
                    "Fetch a code diff from a GitHub pull request",
                    "code-review",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "repo": { "type": "string", "description": "Repository name (owner/repo)" },
                        "pr_number": { "type": "integer", "description": "Pull request number" }
                      },
                      "required": ["repo", "pr_number"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String repo = (String) parameters.get("repo");
            Number prNumber = (Number) parameters.get("pr_number");

            try {
                GHRepository repository = gitHubClientProvider.getClient().getRepository(repo);
                GHPullRequest pr = repository.getPullRequest(prNumber.intValue());

                var sb = new StringBuilder();
                sb.append("PR #%d: %s\n".formatted(pr.getNumber(), pr.getTitle()));
                sb.append("Author: %s\n".formatted(pr.getUser().getLogin()));
                sb.append("Base: %s ← Head: %s\n\n".formatted(pr.getBase().getRef(), pr.getHead().getRef()));

                for (GHPullRequestFileDetail file : pr.listFiles()) {
                    sb.append("--- %s (%s) +%d -%d\n".formatted(
                            file.getFilename(),
                            file.getStatus(),
                            file.getAdditions(),
                            file.getDeletions()));
                    String patch = file.getPatch();
                    if (patch != null) {
                        sb.append(patch).append("\n\n");
                    }
                }

                return new ToolResult.Success(sb.toString());
            } catch (Exception e) {
                log.error("Failed to fetch diff for {}/#{}", repo, prNumber, e);
                return new ToolResult.Error("Failed to fetch diff: " + e.getMessage());
            }
        }
    }
}
