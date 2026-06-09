package io.jaiclaw.shell.commands.setup.config;

import java.nio.file.Path;

public final class ConfigLocation {

    private ConfigLocation() {}

    public static Path defaultDir() {
        String override = System.getenv("JAICLAW_HOME");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".jaiclaw");
    }

    public static Path yamlFile(Path configDir) {
        return configDir.resolve("application-local.yml");
    }

    public static Path envFile(Path configDir) {
        return configDir.resolve(".env");
    }
}
