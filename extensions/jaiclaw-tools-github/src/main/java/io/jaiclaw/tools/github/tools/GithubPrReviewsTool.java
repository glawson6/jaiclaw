package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHPullRequestReview;

import java.util.Map;
import java.util.Set;

/**
 * List formal PR reviews (approve / request-changes / comment) with reviewer,
 * state, submission timestamp, and top-level body. Inline line comments are
 * exposed separately by {@link GithubPrReviewCommentsTool}.
 */
public class GithubPrReviewsTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "pr": { "type": "integer", "description": "Pull request number" }
              },
              "required": ["repo", "pr"]
            }""";

    public GithubPrReviewsTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_pr_reviews",
                "List formal PR reviews (approve / request-changes / comment) with reviewer, state, submission time, and top-level body.",
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
        for (GHPullRequestReview review : repo(repo).getPullRequest(number).listReviews().toList()) {
            count++;
            String login;
            try {
                login = review.getUser() == null ? "(unknown)" : review.getUser().getLogin();
            } catch (Exception e) {
                login = "(error)";
            }
            sb.append("---\n");
            sb.append("Review #").append(Long.toString(review.getId()))
                    .append(" by ").append(login)
                    .append(" — ").append(review.getState());
            try {
                java.util.Date submittedAt = review.getSubmittedAt();
                if (submittedAt != null) {
                    sb.append(" at ").append(submittedAt.toString());
                }
            } catch (Exception ignored) {
                // submittedAt may not be available on PENDING reviews
            }
            sb.append('\n');
            String body = review.getBody();
            if (body != null && !body.isBlank()) {
                sb.append(body).append('\n');
            }
        }
        if (count == 0) {
            sb.append("(no reviews)\n");
        }
        return new ToolResult.Success(sb.toString());
    }
}
