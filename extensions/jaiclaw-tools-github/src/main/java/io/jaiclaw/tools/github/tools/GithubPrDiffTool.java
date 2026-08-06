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
 * Fetch the concatenated unified-diff patch for a pull request.
 * Assembles per-file patches (which is what GitHub's REST API returns);
 * skips binary files.
 */
public class GithubPrDiffTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "pr": { "type": "integer", "description": "Pull request number" }
              },
              "required": ["repo", "pr"]
            }""";

    public GithubPrDiffTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_pr_diff",
                "Fetch the unified-diff patch for a pull request (concatenated per-file patches, binary files skipped).",
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
        int fileCount = 0;
        int skippedBinary = 0;
        for (GHPullRequestFileDetail file : repo(repo).getPullRequest(number).listFiles().toList()) {
            fileCount++;
            String patch = file.getPatch();
            if (patch == null || patch.isEmpty()) {
                skippedBinary++;
                sb.append("diff --git a/").append(file.getFilename())
                        .append(" b/").append(file.getFilename()).append('\n');
                sb.append("(binary or no patch: status=").append(file.getStatus()).append(")\n");
                continue;
            }
            sb.append("diff --git a/").append(file.getFilename())
                    .append(" b/").append(file.getFilename()).append('\n');
            sb.append(patch).append('\n');
        }
        if (fileCount == 0) {
            return new ToolResult.Success("(no changed files)");
        }
        return new ToolResult.Success(sb.toString());
    }
}
