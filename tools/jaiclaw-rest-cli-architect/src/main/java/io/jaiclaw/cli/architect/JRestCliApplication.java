package io.jaiclaw.cli.architect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone entry point for the REST CLI Architect.
 * Only used when built with {@code -Pstandalone} profile.
 */
@SpringBootApplication
public class JRestCliApplication {
    public static void main(String[] args) {
        SpringApplication.run(JRestCliApplication.class, args);
    }
}
