package io.jaiclaw.cli.architect;

import io.jaiclaw.cli.architect.templates.ConfigTemplates;
import io.jaiclaw.cli.architect.templates.JavaTemplates;
import io.jaiclaw.cli.architect.templates.PomTemplates;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure logic: takes a validated {@link ProjectSpec} and returns a map of
 * relative file paths to their generated content. No Spring, no I/O.
 */
public final class ProjectGenerator {

    private ProjectGenerator() {}

    /**
     * Generate all files for the given spec.
     *
     * @param spec validated project specification
     * @return map of relative path to file content
     */
    public static Map<String, String> generate(ProjectSpec spec) {
        return switch (spec.mode()) {
            case JBANG -> generateJBang(spec);
            default -> generateMaven(spec);
        };
    }

    private static Map<String, String> generateMaven(ProjectSpec spec) {
        var files = new LinkedHashMap<String, String>();
        String srcPrefix = "src/main/java/" + packageToPath(spec);
        String testPrefix = "src/test/groovy/" + packageToPath(spec);

        // pom.xml
        String pom = PomTemplates.generate(spec);
        if (!pom.isBlank()) {
            files.put("pom.xml", pom);
        }

        // Application.java (standalone and external modes)
        if (spec.mode() == ProjectMode.STANDALONE || spec.mode() == ProjectMode.EXTERNAL_SUBMODULE) {
            files.put(srcPrefix + "/" + capitalize(spec.name()) + "CliApplication.java",
                    JavaTemplates.application(spec));
        }

        // ApiClient.java
        files.put(srcPrefix + "/" + capitalize(spec.name()) + "ApiClient.java",
                JavaTemplates.apiClient(spec));

        // Commands.java
        files.put(srcPrefix + "/" + capitalize(spec.name()) + "Commands.java",
                JavaTemplates.commands(spec, spec.endpoints()));

        // application.yml
        files.put("src/main/resources/application.yml",
                ConfigTemplates.applicationYml(spec));

        // .env.example
        files.put(".env.example", ConfigTemplates.envExample(spec));

        // Test stub
        files.put(testPrefix + "/" + capitalize(spec.name()) + "CommandsSpec.groovy",
                ConfigTemplates.spockTestStub(spec));

        return files;
    }

    private static Map<String, String> generateJBang(ProjectSpec spec) {
        var files = new LinkedHashMap<String, String>();
        files.put(capitalize(spec.name()) + "Cli.java", JavaTemplates.jbangScript(spec));
        return files;
    }

    private static String packageToPath(ProjectSpec spec) {
        String pkg = JavaTemplates.resolvePackage(spec);
        return pkg.replace('.', '/');
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
