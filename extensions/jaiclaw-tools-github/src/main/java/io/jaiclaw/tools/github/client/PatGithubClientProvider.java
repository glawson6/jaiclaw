package io.jaiclaw.tools.github.client;

import io.jaiclaw.tools.github.GithubToolsProperties;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Default {@link GithubClientProvider} — resolves a PAT from
 * {@link GithubToolsProperties#token()} first, then the {@code GITHUB_TOKEN}
 * environment variable, then falls back to an anonymous client (60 req/hr
 * rate limit).
 *
 * <p>The client is built lazily and cached for the lifetime of this
 * provider. If the PAT changes at runtime, restart the process.
 */
public class PatGithubClientProvider implements GithubClientProvider {

    private static final Logger log = LoggerFactory.getLogger(PatGithubClientProvider.class);

    private final GithubToolsProperties properties;
    private volatile GitHub cached;

    public PatGithubClientProvider(GithubToolsProperties properties) {
        this.properties = properties;
    }

    @Override
    public GitHub getClient() throws IOException {
        GitHub local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = build();
            }
            return cached;
        }
    }

    private GitHub build() throws IOException {
        String token = resolveToken();
        GitHubBuilder builder = new GitHubBuilder().withEndpoint(properties.apiUrl());
        if (token != null && !token.isBlank()) {
            builder = builder.withOAuthToken(token);
            log.info("GitHub client initialised with PAT ({} chars)", token.length());
        } else {
            log.warn("GitHub client initialised WITHOUT a token — rate-limited to 60 req/hr. "
                    + "Set jaiclaw.github.token or GITHUB_TOKEN.");
        }
        return builder.build();
    }

    private String resolveToken() {
        if (properties.token() != null && !properties.token().isBlank()
                && !"not-set".equals(properties.token())) {
            return properties.token();
        }
        String env = System.getenv("GITHUB_TOKEN");
        return env != null && !env.isBlank() ? env : null;
    }
}
