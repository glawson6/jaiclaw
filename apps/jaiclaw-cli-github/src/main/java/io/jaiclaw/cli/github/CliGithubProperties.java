package io.jaiclaw.cli.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the GitHub slash-command CLI. Values come from
 * {@code jaiclaw.cli-github.*} in application.yml OR
 * {@code JAICLAW_CLI_GITHUB_*} env vars.
 *
 * <p>Single public constructor per the Boot 4 record-binder rule.
 */
@ConfigurationProperties(prefix = "jaiclaw.cli-github")
public record CliGithubProperties(
        String systemPromptFile,
        int maxThreadHistory
) {

    public CliGithubProperties {
        if (systemPromptFile == null || systemPromptFile.isBlank()) {
            systemPromptFile = "classpath:prompts/default-system-prompt.md";
        }
        if (maxThreadHistory <= 0) {
            maxThreadHistory = 20;
        }
    }

    /** Programmatic defaults for tests — never seen by the record binder. */
    public static CliGithubProperties defaults() {
        return new CliGithubProperties("classpath:prompts/default-system-prompt.md", 20);
    }
}
