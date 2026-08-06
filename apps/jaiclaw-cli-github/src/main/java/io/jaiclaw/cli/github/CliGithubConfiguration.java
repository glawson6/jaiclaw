package io.jaiclaw.cli.github;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the CLI-github properties so the record binder actually populates
 * them. Component scan for handlers/commands lives on
 * {@code JaiClawCliGithubApplication}.
 */
@Configuration
@EnableConfigurationProperties(CliGithubProperties.class)
public class CliGithubConfiguration {
}
