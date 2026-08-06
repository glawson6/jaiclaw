package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHContent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read the contents of a file in a repository via the GitHub API. Use when
 * the workflow runs without a checkout, or when you need a specific ref
 * (branch/tag/SHA) other than what's on disk.
 */
public class GithubRepoGetContentTool extends AbstractGithubTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "repo": { "type": "string", "description": "Full repository name, e.g. 'owner/name'" },
                "path": { "type": "string", "description": "Path within the repo, e.g. 'docs/README.md'" },
                "ref": { "type": "string", "description": "Optional branch, tag, or commit SHA (default: default branch)" }
              },
              "required": ["repo", "path"]
            }""";

    public GithubRepoGetContentTool(GithubClientProvider clientProvider) {
        super(new ToolDefinition(
                "github_repo_get_content",
                "Read a file's contents from a repository via the GitHub API. Use when the workflow has no checkout or you need a specific ref.",
                ToolCatalog.SECTION_GITHUB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String repo = requireParam(parameters, "repo");
        String path = requireParam(parameters, "path");
        String ref = optionalParam(parameters, "ref", null);

        GHContent content = (ref == null || ref.isBlank())
                ? repo(repo).getFileContent(path)
                : repo(repo).getFileContent(path, ref);
        if (!content.isFile()) {
            return new ToolResult.Success("(path '" + path + "' is a directory, not a file)");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(content.read(), StandardCharsets.UTF_8))) {
            String body = reader.lines().collect(Collectors.joining("\n"));
            return new ToolResult.Success(body);
        }
    }
}
