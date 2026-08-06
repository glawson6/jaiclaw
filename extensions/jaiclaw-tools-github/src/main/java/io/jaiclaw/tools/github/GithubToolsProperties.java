package io.jaiclaw.tools.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the JaiClaw GitHub tools. Values come from
 * {@code jaiclaw.github.*} properties or the {@code GITHUB_TOKEN}
 * environment variable (Spring's relaxed binding).
 *
 * <p>Single public constructor per the Boot 4 record-binder rule.
 */
@ConfigurationProperties(prefix = "jaiclaw.github")
public record GithubToolsProperties(
        boolean enabled,
        String token,
        String apiUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {

    public GithubToolsProperties {
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = "https://api.github.com";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
        if (readTimeoutSeconds <= 0) {
            readTimeoutSeconds = 30;
        }
    }

    /** Programmatic defaults for tests and builders — never seen by the record binder. */
    public static GithubToolsProperties defaults() {
        return new GithubToolsProperties(false, null, "https://api.github.com", 10, 30);
    }
}
