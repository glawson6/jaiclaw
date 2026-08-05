package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitComment;

import java.util.Map;
import java.util.Set;

/**
 * Post a comment on a specific commit. Optionally inline at a file path + line
 * (equivalent to a commit-page "add comment" on a specific line of the diff).
 */
public class GithubCommitCommentCreateTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "sha": { "type": "string", "description": "Commit SHA" },
                "body": { "type": "string", "description": "Markdown body of the comment" },
                "path": { "type": "string", "description": "Optional file path to anchor the comment to a specific file in the diff" },
                "line": { "type": "integer", "description": "Optional line number within path (requires path)" }
              },
              "required": ["repo", "sha", "body"]
            }""";

    public GithubCommitCommentCreateTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_commit_comment_create",
                "Post a comment on a specific commit. Optionally anchor it inline at a file path + line.",
                ToolCatalog.SECTION_GITHUB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String repo = requireParam(parameters, "repo");
        String sha = requireParam(parameters, "sha");
        String body = requireParam(parameters, "body");
        String path = optionalParam(parameters, "path", null);
        Integer line = optionalIntParam(parameters, "line");

        GHCommit commit = repo(repo).getCommit(sha);
        GHCommitComment comment;
        if (path != null && !path.isBlank()) {
            comment = commit.createComment(body, path, line, null);
        } else {
            comment = commit.createComment(body);
        }
        return new ToolResult.Success(
                "Posted commit comment on " + repo + "@" + sha
                        + " (" + body.length() + " chars). URL: " + comment.getHtmlUrl());
    }
}
