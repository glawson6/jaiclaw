package io.jaiclaw.tools.github.tools;

import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.Map;

/**
 * Base class for GitHub tools that holds a reference to the client provider
 * and exposes a common {@code repo("owner/name")} helper.
 */
public abstract class AbstractGithubTool extends AbstractBuiltinTool {

    protected final GithubClientProvider clientProvider;

    protected AbstractGithubTool(ToolDefinition definition, GithubClientProvider clientProvider) {
        super(definition);
        this.clientProvider = clientProvider;
    }

    protected GHRepository repo(String repo) throws IOException {
        GitHub client = clientProvider.getClient();
        return client.getRepository(repo);
    }

    protected int intParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString());
    }

    protected Integer optionalIntParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString());
    }
}
