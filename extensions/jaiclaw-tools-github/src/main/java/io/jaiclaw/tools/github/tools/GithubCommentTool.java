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
 * Post a comment on a GitHub issue or PR (PRs are issues in the API).
 */
public class GithubCommentTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": {
                  "type": "string",
                  "description": "Full repository name, e.g. 'owner/name'"
                },
                "issue": {
                  "type": "integer",
                  "description": "Issue or pull request number"
                },
                "body": {
                  "type": "string",
                  "description": "Markdown body of the comment"
                }
              },
              "required": ["repo", "issue", "body"]
            }""";

    public GithubCommentTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_comment",
                "Post a Markdown comment on a GitHub issue or pull request thread.",
                ToolCatalog.SECTION_GITHUB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String repo = requireParam(parameters, "repo");
        int number = intParam(parameters, "issue");
        String body = requireParam(parameters, "body");

        GHIssue issue = repo(repo).getIssue(number);
        GHIssueComment comment = issue.comment(body);
        return new ToolResult.Success(
                "Posted comment to " + repo + " #" + number
                        + " (" + body.length() + " chars). URL: " + comment.getHtmlUrl());
    }
}
