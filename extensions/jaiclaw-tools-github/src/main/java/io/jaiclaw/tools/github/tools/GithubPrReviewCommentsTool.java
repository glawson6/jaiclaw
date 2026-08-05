package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHPullRequestReviewComment;

import java.util.Map;
import java.util.Set;

/**
 * List inline PR review comments — the line-anchored comments that show up
 * next to specific lines in the "Files changed" tab. Distinct from the
 * thread comments returned by {@link GithubPrThreadTool}.
 */
public class GithubPrReviewCommentsTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "pr": { "type": "integer", "description": "Pull request number" }
              },
              "required": ["repo", "pr"]
            }""";

    public GithubPrReviewCommentsTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_pr_review_comments",
                "List inline PR review comments (line-anchored, shown in the Files-changed tab). Each includes path, line, author, and body.",
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
        for (GHPullRequestReviewComment comment : repo(repo).getPullRequest(number).listReviewComments().toList()) {
            count++;
            String login;
            try {
                login = comment.getUser() == null ? "(unknown)" : comment.getUser().getLogin();
            } catch (Exception e) {
                login = "(error)";
            }
            sb.append("---\n");
            java.util.Date createdAt = comment.getCreatedAt();
            sb.append("Review comment #").append(Long.toString(comment.getId()))
                    .append(" by ").append(login)
                    .append(" at ").append(createdAt == null ? "?" : createdAt.toString())
                    .append('\n');
            sb.append("Path: ").append(comment.getPath())
                    .append("  Line: ").append(comment.getLine())
                    .append(comment.getSide() == null ? "" : "  Side: " + comment.getSide())
                    .append('\n');
            sb.append(comment.getBody()).append('\n');
        }
        if (count == 0) {
            sb.append("(no inline review comments)\n");
        }
        return new ToolResult.Success(sb.toString());
    }
}
