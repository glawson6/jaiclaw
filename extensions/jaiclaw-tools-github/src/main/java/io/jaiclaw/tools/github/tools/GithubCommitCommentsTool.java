package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHCommitComment;

import java.util.Map;
import java.util.Set;

/**
 * List comments attached directly to a specific commit (shown on the commit
 * page, not tied to any PR). Distinct from PR thread comments and PR
 * review comments.
 */
public class GithubCommitCommentsTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "sha": { "type": "string", "description": "Commit SHA (full or short)" }
              },
              "required": ["repo", "sha"]
            }""";

    public GithubCommitCommentsTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_commit_comments",
                "List comments attached to a specific commit (from the commit page, independent of PRs).",
                ToolCatalog.SECTION_GITHUB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String repo = requireParam(parameters, "repo");
        String sha = requireParam(parameters, "sha");

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (GHCommitComment comment : repo(repo).listCommitComments(sha).toList()) {
            count++;
            String login;
            try {
                login = comment.getUser() == null ? "(unknown)" : comment.getUser().getLogin();
            } catch (Exception e) {
                login = "(error)";
            }
            sb.append("---\n");
            java.util.Date createdAt = comment.getCreatedAt();
            sb.append("Commit comment #").append(Long.toString(comment.getId()))
                    .append(" by ").append(login)
                    .append(" at ").append(createdAt == null ? "?" : createdAt.toString())
                    .append('\n');
            if (comment.getPath() != null && !comment.getPath().isBlank()) {
                sb.append("Path: ").append(comment.getPath());
                if (comment.getLine() > 0) {
                    sb.append("  Line: ").append(comment.getLine());
                }
                sb.append('\n');
            }
            sb.append(comment.getBody()).append('\n');
        }
        if (count == 0) {
            sb.append("(no comments on commit ").append(sha).append(")\n");
        }
        return new ToolResult.Success(sb.toString());
    }
}
