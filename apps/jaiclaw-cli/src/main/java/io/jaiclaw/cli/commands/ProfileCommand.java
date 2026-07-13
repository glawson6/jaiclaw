package io.jaiclaw.cli.commands;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.shell.core.command.annotation.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Profile management commands — create, list, and switch between configuration profiles.
 * Each profile is an isolated directory under ~/.jaiclaw/profiles/ containing
 * its own application-local.yml and .env file.
 */
@Component
public class ProfileCommand {

    private static final String DEFAULT_PROFILE = "default";

    @Command(name = "profile list", alias = "profile-list", description = "List available profiles")
    public String list() {
        Path profilesDir = profilesDir();
        if (!Files.isDirectory(profilesDir)) {
            return "No profiles directory found at " + profilesDir;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Profiles (%s)\n".formatted(profilesDir));
        sb.append("─────────────────────────\n");

        String activeProfile = activeProfile();

        try (Stream<Path> paths = Files.list(profilesDir)) {
            paths.filter(Files::isDirectory)
                    .sorted()
                    .forEach(dir -> {
                        String name = dir.getFileName().toString();
                        boolean hasConfig = Files.exists(dir.resolve("application-local.yml"));
                        boolean hasEnv = Files.exists(dir.resolve(".env"));
                        String marker = name.equals(activeProfile) ? " *" : "  ";
                        String details = (hasConfig ? "yml" : "---") + " " + (hasEnv ? "env" : "---");
                        sb.append("%s %-20s [%s]\n".formatted(marker, name, details));
                    });
        } catch (IOException e) {
            return "Error listing profiles: " + e.getMessage();
        }

        return sb.toString();
    }

    @Command(name = "profile create", alias = "profile-create", description = "Create a new profile")
    public String create(@Option(description = "Profile name") String name) {
        Path profileDir = profilesDir().resolve(name);
        if (Files.exists(profileDir)) {
            return "Profile already exists: " + name;
        }

        try {
            Files.createDirectories(profileDir);

            // Create template application-local.yml
            String yamlTemplate = """
                    # JaiClaw configuration — profile: %s
                    # Edit this file to configure your LLM provider and other settings.
                    jaiclaw:
                      identity:
                        name: JaiClaw
                      security:
                        mode: none

                    spring:
                      ai:
                        anthropic:
                          api-key: ${ANTHROPIC_API_KEY:not-set}
                    """.formatted(name);
            Files.writeString(profileDir.resolve("application-local.yml"), yamlTemplate);

            // Create template .env
            String envTemplate = """
                    # JaiClaw secrets — profile: %s
                    # Source this file before running JaiClaw: source %s/.env

                    # export ANTHROPIC_API_KEY=sk-ant-...
                    # export OPENAI_API_KEY=sk-...
                    """.formatted(name, profileDir);
            Files.writeString(profileDir.resolve(".env"), envTemplate);

            // Create sessions directory
            Files.createDirectories(profileDir.resolve("sessions"));

            return "Created profile: " + name + "\n  Location: " + profileDir
                    + "\n  Run 'jaiclaw --profile " + name + " setup' to configure it.";
        } catch (IOException e) {
            return "Error creating profile: " + e.getMessage();
        }
    }

    @Command(name = "profile switch", alias = "profile-switch", description = "Switch to a different profile")
    public String switchProfile(@Option(description = "Profile name") String name) {
        Path profileDir = profilesDir().resolve(name);
        if (!Files.isDirectory(profileDir)) {
            return "Profile not found: " + name + ". Use 'profile create " + name + "' first.";
        }

        Path configFile = jaiclawhome().resolve("config.yaml");
        try {
            String config = "# JaiClaw CLI config\nactive-profile: %s\n".formatted(name);
            Files.writeString(configFile, config);
            return "Switched to profile: " + name
                    + "\n  Config dir: " + profileDir;
        } catch (IOException e) {
            return "Error switching profile: " + e.getMessage();
        }
    }

    @Command(name = "profile show", alias = "profile-show", description = "Show the current active profile")
    public String show() {
        String active = activeProfile();
        Path profileDir = profilesDir().resolve(active);
        StringBuilder sb = new StringBuilder();
        sb.append("Active Profile: %s\n".formatted(active));
        sb.append("  Directory:    %s\n".formatted(profileDir));
        sb.append("  Config:       %s\n".formatted(
                Files.exists(profileDir.resolve("application-local.yml")) ? "present" : "missing"));
        sb.append("  Env file:     %s\n".formatted(
                Files.exists(profileDir.resolve(".env")) ? "present" : "missing"));
        return sb.toString();
    }

    private Path jaiclawhome() {
        String override = System.getenv("JAICLAW_HOME");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".jaiclaw");
    }

    private Path profilesDir() {
        return jaiclawhome().resolve("profiles");
    }

    private String activeProfile() {
        // Check env var first
        String envProfile = System.getenv("JAICLAW_PROFILE");
        if (envProfile != null && !envProfile.isBlank()) {
            return envProfile;
        }

        // Read from config.yaml
        Path configFile = jaiclawhome().resolve("config.yaml");
        if (Files.exists(configFile)) {
            try {
                String content = Files.readString(configFile);
                for (String line : content.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("active-profile:")) {
                        String value = trimmed.substring("active-profile:".length()).trim();
                        if (!value.isBlank()) {
                            return value;
                        }
                    }
                }
            } catch (IOException e) {
                // Fall through to default
            }
        }

        return DEFAULT_PROFILE;
    }
}
