package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHPullRequestCommitDetail;

import java.util.Map;
import java.util.Set;

/**
 * List commits included in a pull request with author + short SHA + message.
 */
public class GithubPrCommitsTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "pr": { "type": "integer", "description": "Pull request number" }
              },
              "required": ["repo", "pr"]
            }""";

    public GithubPrCommitsTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_pr_commits",
                "List commits included in a pull request with author, short SHA, and commit message.",
                ToolCatalog.SECTION_GITHUB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String repo = requireParam(parameters, "repo");
        int number = intParam(parameters, "pr");

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (GHPullRequestCommitDetail commit : repo(repo).getPullRequest(number).listCommits().toList()) {
            count++;
            String sha = commit.getSha();
            String shortSha = sha == null ? "?" : sha.substring(0, Math.min(7, sha.length()));
            GHPullRequestCommitDetail.Commit c = commit.getCommit();
            @SuppressWarnings("deprecation")
            org.kohsuke.github.GitUser gitUser = c == null ? null : c.getAuthor();
            String author = gitUser == null ? "(unknown)" : gitUser.getName();
            String message = c == null ? "" : c.getMessage();
            String firstLine = message.contains("\n") ? message.substring(0, message.indexOf('\n')) : message;
            sb.append(shortSha).append("  ").append(author).append("  ").append(firstLine).append('\n');
        }
        if (count == 0) {
            return new ToolResult.Success("(no commits in PR)");
        }
        return new ToolResult.Success(sb.toString());
    }
}
