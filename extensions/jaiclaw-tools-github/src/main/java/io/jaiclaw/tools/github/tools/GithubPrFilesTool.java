package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHPullRequestFileDetail;

import java.util.Map;
import java.util.Set;

/**
 * List changed files in a pull request with per-file status + additions/deletions.
 * Complements {@link GithubPrDiffTool} for when only the file list is needed
 * (much smaller payload).
 */
public class GithubPrFilesTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "pr": { "type": "integer", "description": "Pull request number" }
              },
              "required": ["repo", "pr"]
            }""";

    public GithubPrFilesTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_pr_files",
                "List changed files in a pull request with status (added/modified/removed) and additions/deletions counts.",
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
        sb.append(String.format("%-8s %-6s %-6s %s%n", "STATUS", "+ADDS", "-DELS", "FILE"));
        sb.append("-".repeat(80)).append('\n');
        int count = 0;
        for (GHPullRequestFileDetail file : repo(repo).getPullRequest(number).listFiles().toList()) {
            count++;
            sb.append(String.format("%-8s %-6d %-6d %s%n",
                    file.getStatus(), file.getAdditions(), file.getDeletions(), file.getFilename()));
        }
        if (count == 0) {
            return new ToolResult.Success("(no changed files)");
        }
        return new ToolResult.Success(sb.toString());
    }
}
