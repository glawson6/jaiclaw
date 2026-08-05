package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHUser;

import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Fetch full metadata for a GitHub issue or pull request — title, body,
 * state, author, labels, assignees, milestone, timestamps, reaction counts.
 * Works for both issues and PRs (PRs are issues in the API).
 */
public class GithubIssueGetTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "issue": { "type": "integer", "description": "Issue or pull request number" }
              },
              "required": ["repo", "issue"]
            }""";

    public GithubIssueGetTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_issue_get",
                "Fetch full metadata for a GitHub issue or PR: title, body, state, author, labels, assignees, milestone, timestamps.",
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
        sb.append("Repository: ").append(repo).append('\n');
        sb.append("Issue: #").append(number).append('\n');
        sb.append("Title: ").append(issue.getTitle()).append('\n');
        sb.append("State: ").append(issue.getState()).append('\n');
        sb.append("Author: ").append(userLogin(issue.getUser())).append('\n');
        java.util.Date createdAt = issue.getCreatedAt();
        java.util.Date updatedAt = issue.getUpdatedAt();
        sb.append("Created: ").append(createdAt == null ? "?" : createdAt.toString()).append('\n');
        sb.append("Updated: ").append(updatedAt == null ? "?" : updatedAt.toString()).append('\n');

        String assignees = issue.getAssignees().stream()
                .map(this::userLogin)
                .collect(Collectors.joining(", "));
        sb.append("Assignees: ").append(assignees.isEmpty() ? "(none)" : assignees).append('\n');

        StringJoiner labels = new StringJoiner(", ");
        for (GHLabel label : issue.getLabels()) {
            labels.add(label.getName());
        }
        sb.append("Labels: ").append(labels.length() == 0 ? "(none)" : labels).append('\n');

        sb.append("Milestone: ").append(issue.getMilestone() == null ? "(none)" : issue.getMilestone().getTitle()).append('\n');
        sb.append("Comments: ").append(issue.getCommentsCount()).append('\n');
        sb.append("URL: ").append(issue.getHtmlUrl()).append('\n');
        sb.append('\n').append("Body:\n").append(issue.getBody() == null ? "(empty)" : issue.getBody()).append('\n');

        return new ToolResult.Success(sb.toString());
    }

    private String userLogin(GHUser user) {
        if (user == null) return "(unknown)";
        try {
            return user.getLogin();
        } catch (Exception e) {
            return "(error)";
        }
    }
}
