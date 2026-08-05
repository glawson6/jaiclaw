package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;

import java.util.Map;
import java.util.Set;

/**
 * Fetch the conversation thread (issue-level comments) for an issue or PR.
 * These are the comments shown on the main issue/PR page — NOT inline PR
 * review comments (see {@link GithubPrReviewCommentsTool}) or commit
 * comments (see {@link GithubCommitCommentsTool}).
 */
public class GithubPrThreadTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "issue": { "type": "integer", "description": "Issue or pull request number" }
              },
              "required": ["repo", "issue"]
            }""";

    public GithubPrThreadTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_pr_thread",
                "Fetch the conversation thread (issue-level comments) on a GitHub issue or PR. Excludes inline review comments and commit comments.",
                ToolCatalog.SECTION_GITHUB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String repo = requireParam(parameters, "repo");
        int number = intParam(parameters, "issue");

        GHIssue issue = repo(repo).getIssue(number);
        StringBuilder sb = new StringBuilder();
        sb.append("Thread: ").append(repo).append(" #").append(number).append('\n');
        int count = 0;
        for (GHIssueComment comment : issue.getComments()) {
            count++;
            String login;
            try {
                login = comment.getUser() == null ? "(unknown)" : comment.getUser().getLogin();
            } catch (Exception e) {
                login = "(error)";
            }
            sb.append("---\n");
            java.util.Date createdAt = comment.getCreatedAt();
            sb.append("Comment #").append(Long.toString(comment.getId()))
                    .append(" by ").append(login)
                    .append(" at ").append(createdAt == null ? "?" : createdAt.toString()).append('\n');
            sb.append(comment.getBody()).append('\n');
        }
        if (count == 0) {
            sb.append("(no comments)\n");
        }
        return new ToolResult.Success(sb.toString());
    }
}
