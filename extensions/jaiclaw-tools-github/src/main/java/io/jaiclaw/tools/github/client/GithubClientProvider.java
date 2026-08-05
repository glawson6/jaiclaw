package io.jaiclaw.tools.github.client;

import org.kohsuke.github.GitHub;

import java.io.IOException;

/**
 * SPI for obtaining a {@link GitHub} client. The default implementation
 * ({@link PatGithubClientProvider}) uses a personal access token. Adopters
 * can drop in a GitHub-App-installation-token provider without touching
 * any of the tool classes that consume this SPI.
 */
public interface GithubClientProvider {

    /**
     * Return a live, cached {@link GitHub} client. Implementations should
     * cache internally — every tool invocation calls this method.
     */
    GitHub getClient() throws IOException;
}
