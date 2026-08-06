package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fetch full metadata for a pull request — mergeable state, base/head refs,
 * requested reviewers, CI status. Complements {@link GithubIssueGetTool}
 * for issue-level fields (title, body, labels, etc.).
 */
public class GithubPrGetTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "pr": { "type": "integer", "description": "Pull request number" }
              },
              "required": ["repo", "pr"]
            }""";

    public GithubPrGetTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_pr_get",
                "Fetch pull-request-specific metadata: mergeable state, base/head, requested reviewers, changed-file counts.",
                ToolCatalog.SECTION_GITHUB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String repo = requireParam(parameters, "repo");
        int number = intParam(parameters, "pr");

        GHPullRequest pr = repo(repo).getPullRequest(number);
        StringBuilder sb = new StringBuilder();
        sb.append("Repository: ").append(repo).append('\n');
        sb.append("PR: #").append(number).append('\n');
        sb.append("Title: ").append(pr.getTitle()).append('\n');
        sb.append("State: ").append(pr.getState()).append('\n');
        sb.append("Draft: ").append(pr.isDraft()).append('\n');
        sb.append("Merged: ").append(pr.isMerged()).append('\n');
        sb.append("Mergeable: ").append(pr.getMergeable() == null ? "unknown" : pr.getMergeable()).append('\n');
        sb.append("Base: ").append(pr.getBase().getRef()).append(" (").append(pr.getBase().getSha()).append(")\n");
        sb.append("Head: ").append(pr.getHead().getRef()).append(" (").append(pr.getHead().getSha()).append(")\n");
        sb.append("Author: ").append(userLogin(pr.getUser())).append('\n');
        sb.append("Additions: ").append(pr.getAdditions())
                .append(", Deletions: ").append(pr.getDeletions())
                .append(", Changed files: ").append(pr.getChangedFiles()).append('\n');
        sb.append("Commits: ").append(pr.getCommits()).append('\n');

        String reviewers = pr.getRequestedReviewers().stream()
                .map(this::userLogin)
                .collect(Collectors.joining(", "));
        sb.append("Requested reviewers: ").append(reviewers.isEmpty() ? "(none)" : reviewers).append('\n');

        sb.append("URL: ").append(pr.getHtmlUrl()).append('\n');
        sb.append('\n').append("Body:\n").append(pr.getBody() == null ? "(empty)" : pr.getBody()).append('\n');

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
