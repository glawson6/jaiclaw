package io.jaiclaw.scaffold.generators;

import io.jaiclaw.scaffold.ProjectManifest;

/**
 * Generates the @SpringBootApplication main class.
 */
public final class ApplicationClassGenerator {

    private ApplicationClassGenerator() {}

    public static String generate(ProjectManifest manifest) {
        return """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class %s {

                    public static void main(String[] args) {
                        SpringApplication.run(%s.class, args);
                    }
                }
                """.formatted(
                manifest.javaPackage(),
                manifest.applicationClassName(),
                manifest.applicationClassName()
        );
    }
}
