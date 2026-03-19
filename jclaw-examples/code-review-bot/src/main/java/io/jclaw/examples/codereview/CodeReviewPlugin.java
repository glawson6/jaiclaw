package io.jclaw.examples.codereview;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.plugin.JClawPlugin;
import io.jclaw.plugin.PluginApi;
import io.jclaw.core.plugin.PluginDefinition;
import io.jclaw.core.plugin.PluginKind;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Plugin that registers a get_diff tool for fetching code diffs.
 * Demonstrates the JClawPlugin SPI for tool registration.
 */
@Component
public class CodeReviewPlugin implements JClawPlugin {

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "code-review-plugin",
                "Code Review Plugin",
                "Provides tools for fetching and analyzing code diffs",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new GetDiffTool());
    }

    /**
     * Tool that simulates fetching a code diff. In production, this would
     * integrate with GitHub/GitLab APIs to fetch actual PR diffs.
     */
    static class GetDiffTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "get_diff",
                    "Fetch a code diff for review (simulated — replace with GitHub/GitLab API)",
                    "code-review",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "repo": { "type": "string", "description": "Repository name (owner/repo)" },
                        "pr_number": { "type": "integer", "description": "Pull request number" }
                      },
                      "required": ["repo", "pr_number"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String repo = (String) parameters.get("repo");
            Number prNumber = (Number) parameters.get("pr_number");

            // Simulated diff — replace with actual GitHub API call
            String diff = String.format("""
                    --- a/src/main/java/com/example/UserService.java
                    +++ b/src/main/java/com/example/UserService.java
                    @@ -15,6 +15,12 @@
                     public class UserService {
                    +    public User findUser(String query) {
                    +        // TODO: add input validation
                    +        String sql = "SELECT * FROM users WHERE name = '" + query + "'";
                    +        return jdbcTemplate.queryForObject(sql, User.class);
                    +    }
                    +
                         public List<User> getAllUsers() {
                    Repository: %s, PR: #%d""", repo, prNumber.intValue());

            return new ToolResult.Success(diff);
        }
    }
}
