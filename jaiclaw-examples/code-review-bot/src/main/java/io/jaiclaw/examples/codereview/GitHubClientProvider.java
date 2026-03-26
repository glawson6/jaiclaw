package io.jaiclaw.examples.codereview;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Provides a shared {@link GitHub} client instance.
 * Reads {@code GITHUB_TOKEN} from the environment for authenticated access;
 * falls back to anonymous access (rate-limited to 60 requests/hour).
 */
@Component
public class GitHubClientProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubClientProvider.class);

    private final GitHub github;

    public GitHubClientProvider() throws IOException {
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            this.github = new GitHubBuilder().withOAuthToken(token).build();
            log.info("GitHub client initialized with token authentication");
        } else {
            this.github = GitHub.connectAnonymously();
            log.warn("No GITHUB_TOKEN set — using anonymous GitHub access (60 requests/hour)");
        }
    }

    public GitHub getClient() {
        return github;
    }
}
