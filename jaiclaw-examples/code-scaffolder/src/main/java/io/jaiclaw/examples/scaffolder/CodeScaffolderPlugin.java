package io.jaiclaw.examples.scaffolder;

import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Code scaffolding plugin demonstrating Spring AI tool loop (default mode)
 * with streaming output and BEFORE_PROMPT_BUILD hook for prompt customization.
 *
 * <p>The agent generates project scaffolding using tools to browse templates
 * and generate files. A modifying hook injects coding standards into the
 * system prompt so the LLM follows them when generating code.
 */
@Component
public class CodeScaffolderPlugin implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(CodeScaffolderPlugin.class);

    private static final String CODING_STANDARDS = """

            ## Coding Standards
            When generating code, follow these conventions:
            - Use 4-space indentation (no tabs)
            - Add Javadoc to all public classes and methods
            - Follow the Google Java Style Guide
            - Use records for value types, sealed interfaces for ADTs
            - Prefer composition over inheritance
            - Include package-info.java for every package
            - Write unit tests alongside production code
            """;

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "code-scaffolder-plugin",
                "Code Scaffolder Plugin",
                "Tools for generating project scaffolding with template support",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new ListTemplatesTool());
        api.registerTool(new ReadTemplateTool());
        api.registerTool(new GenerateFileTool());
        api.registerTool(new CreateProjectStructureTool());

        // Modifying hook: inject coding standards into system prompt
        api.on(HookName.BEFORE_PROMPT_BUILD, (event, ctx) -> {
            if (event instanceof String systemPrompt) {
                log.info("[HOOK] BEFORE_PROMPT_BUILD: injecting coding standards into system prompt");
                return systemPrompt + CODING_STANDARDS;
            }
            return event;
        });
    }

    // ---- Tools ----

    static class ListTemplatesTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "list_templates",
                    "List available project templates for a given language",
                    "scaffolding",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "language": { "type": "string", "description": "Programming language (java, python, typescript, go)" }
                      },
                      "required": ["language"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String language = (String) parameters.get("language");

            return switch (language.toLowerCase()) {
                case "java" -> new ToolResult.Success("""
                        Available Java templates:

                        1. spring-boot-api
                           Description: Spring Boot REST API with OpenAPI, JPA, and Flyway migrations
                           Files: 12 | Includes: pom.xml, Application.java, controller, service, repository, entity

                        2. spring-boot-library
                           Description: Reusable Spring Boot starter library with auto-configuration
                           Files: 8 | Includes: pom.xml, AutoConfiguration, Properties, service interface

                        3. cli-tool
                           Description: Picocli command-line application with GraalVM native-image support
                           Files: 6 | Includes: pom.xml, MainCommand, subcommands, native-image config

                        4. gradle-multimodule
                           Description: Multi-module Gradle project with convention plugins
                           Files: 10 | Includes: build.gradle.kts, settings.gradle.kts, convention plugins
                        """);
                case "python" -> new ToolResult.Success("""
                        Available Python templates:

                        1. fastapi-service
                           Description: FastAPI web service with SQLAlchemy, Alembic, and Pydantic models
                           Files: 10 | Includes: pyproject.toml, main.py, routes, models, migrations

                        2. cli-tool
                           Description: Click-based CLI application with rich output
                           Files: 6 | Includes: pyproject.toml, cli.py, commands, config

                        3. data-pipeline
                           Description: Data pipeline with Pandas, DuckDB, and Prefect orchestration
                           Files: 8 | Includes: pyproject.toml, pipeline.py, transforms, tests
                        """);
                default -> new ToolResult.Success(
                        "Templates available for: java, python, typescript, go. Try one of these languages.");
            };
        }
    }

    static class ReadTemplateTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "read_template",
                    "Read the content of a specific project template",
                    "scaffolding",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string", "description": "Template name (e.g. spring-boot-api)" }
                      },
                      "required": ["name"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String name = (String) parameters.get("name");

            if ("spring-boot-api".equals(name)) {
                return new ToolResult.Success("""
                        Template: spring-boot-api

                        Structure:
                          {{projectName}}/
                            pom.xml
                            src/main/java/{{packagePath}}/
                              {{ProjectName}}Application.java
                              config/
                                OpenApiConfig.java
                              controller/
                                {{Entity}}Controller.java
                              service/
                                {{Entity}}Service.java
                              repository/
                                {{Entity}}Repository.java
                              model/
                                {{Entity}}.java
                            src/main/resources/
                              application.yml
                              db/migration/
                                V1__init.sql
                            src/test/java/{{packagePath}}/
                              {{Entity}}ControllerTest.java
                              {{Entity}}ServiceTest.java

                        Variables:
                          - projectName: Project name (kebab-case)
                          - packagePath: Java package path (e.g., com/example/myapp)
                          - ProjectName: Project name (PascalCase)
                          - Entity: Primary entity name (PascalCase)
                        """);
            }

            return new ToolResult.Success("Template '%s' found. Use list_templates to see available options.".formatted(name));
        }
    }

    static class GenerateFileTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "generate_file",
                    "Generate a file with the given content at the specified path",
                    "scaffolding",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "path": { "type": "string", "description": "File path relative to project root" },
                        "content": { "type": "string", "description": "File content to generate" },
                        "language": { "type": "string", "description": "Programming language for syntax highlighting" }
                      },
                      "required": ["path", "content"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String path = (String) parameters.get("path");
            String content = (String) parameters.get("content");
            String language = parameters.containsKey("language") ? (String) parameters.get("language") : "unknown";

            int lines = content.split("\n").length;
            return new ToolResult.Success(
                    "Generated: %s (%d lines, %s). File written successfully.".formatted(path, lines, language));
        }
    }

    static class CreateProjectStructureTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "create_project_structure",
                    "Create the directory structure for a new project from a template",
                    "scaffolding",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string", "description": "Project name" },
                        "type": { "type": "string", "description": "Template type (e.g. spring-boot-api)" },
                        "language": { "type": "string", "description": "Programming language" }
                      },
                      "required": ["name", "type", "language"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String name = (String) parameters.get("name");
            String type = (String) parameters.get("type");
            String language = (String) parameters.get("language");

            return new ToolResult.Success("""
                    Project structure created: %s (template: %s, language: %s)

                    %s/
                      pom.xml
                      src/
                        main/
                          java/
                            io/example/%s/
                              %sApplication.java
                              config/
                              controller/
                              service/
                              repository/
                              model/
                          resources/
                            application.yml
                            db/migration/
                        test/
                          java/
                            io/example/%s/
                      .gitignore
                      README.md

                    Directories created: 12
                    Skeleton files generated: 3 (pom.xml, Application.java, application.yml)

                    Next: Use generate_file to create individual source files.
                    """.formatted(name, type, language,
                    name,
                    name.replace("-", ""),
                    capitalize(name),
                    name.replace("-", "")));
        }

        private static String capitalize(String s) {
            if (s == null || s.isEmpty()) return s;
            String cleaned = s.replace("-", " ");
            var sb = new StringBuilder();
            for (String word : cleaned.split("\\s+")) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return sb.toString();
        }
    }
}
