package io.jaiclaw.skill.creator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone entry point for the Skill Creator CLI.
 * Only used when built with {@code -Pstandalone} profile.
 */
@SpringBootApplication
public class SkillCreatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillCreatorApplication.class, args);
    }
}
