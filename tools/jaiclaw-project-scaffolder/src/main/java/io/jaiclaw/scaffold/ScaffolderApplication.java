package io.jaiclaw.scaffold;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone entry point for the Project Scaffolder CLI.
 * Only used when built with {@code -Pstandalone} profile.
 */
@SpringBootApplication
public class ScaffolderApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScaffolderApplication.class, args);
    }
}
