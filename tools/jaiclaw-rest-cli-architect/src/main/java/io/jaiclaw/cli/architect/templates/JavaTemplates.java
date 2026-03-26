package io.jaiclaw.cli.architect.templates;

import io.jaiclaw.cli.architect.AuthConfig;
import io.jaiclaw.cli.architect.EndpointSpec;
import io.jaiclaw.cli.architect.ProjectSpec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Text block templates for generated Java source files:
 * Application.java, ApiClient.java, Commands.java, and tool inner classes.
 */
public final class JavaTemplates {

    private JavaTemplates() {}

    public static String application(ProjectSpec spec) {
        String pkg = resolvePackage(spec);
        String className = capitalize(spec.name()) + "CliApplication";
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
                """.formatted(pkg, className, className);
    }

    public static String apiClient(ProjectSpec spec) {
        String pkg = resolvePackage(spec);
        String className = capitalize(spec.name()) + "ApiClient";
        String prefix = spec.name().toUpperCase();
        String baseUrl = spec.api() != null && spec.api().baseUrl() != null
                ? spec.api().baseUrl() : "https://api.example.com";

        String authSetup = authClientSetup(spec.auth(), prefix);
        String authParams = authConstructorParams(spec.auth(), prefix);

        return """
                package %s;

                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Component;
                import org.springframework.web.client.RestClient;

                @Component
                public class %s {

                    private final RestClient restClient;

                    public %s(
                            @Value("${%s.api.base-url:%s}") String baseUrl%s) {

                        RestClient.Builder builder = RestClient.builder()
                                .baseUrl(baseUrl);
                %s
                        this.restClient = builder.build();
                    }

                    public String get(String path, Object... uriVars) {
                        return restClient.get().uri(path, uriVars).retrieve().body(String.class);
                    }

                    public String post(String path, Object body) {
                        return restClient.post().uri(path)
                                .header("Content-Type", "application/json")
                                .body(body)
                                .retrieve().body(String.class);
                    }

                    public String put(String path, Object body, Object... uriVars) {
                        return restClient.put().uri(path, uriVars)
                                .header("Content-Type", "application/json")
                                .body(body)
                                .retrieve().body(String.class);
                    }

                    public String delete(String path, Object... uriVars) {
                        return restClient.delete().uri(path, uriVars).retrieve().body(String.class);
                    }
                }
                """.formatted(pkg, className, className, spec.name(), baseUrl, authParams, authSetup);
    }

    public static String commands(ProjectSpec spec, List<EndpointSpec> endpoints) {
        String pkg = resolvePackage(spec);
        String nameCapitalized = capitalize(spec.name());
        String clientClass = nameCapitalized + "ApiClient";
        String commandsClass = nameCapitalized + "Commands";

        var methodsBuilder = new StringBuilder();
        var toolClassesBuilder = new StringBuilder();

        for (var ep : endpoints) {
            methodsBuilder.append(commandMethod(spec.name(), ep));
            toolClassesBuilder.append(toolInnerClass(spec.name(), nameCapitalized, commandsClass, ep));
        }

        return """
                package %s;

                import com.fasterxml.jackson.databind.ObjectMapper;
                import io.jaiclaw.core.tool.ToolCallback;
                import io.jaiclaw.core.tool.ToolContext;
                import io.jaiclaw.core.tool.ToolDefinition;
                import io.jaiclaw.core.tool.ToolResult;
                import org.springframework.shell.standard.ShellComponent;
                import org.springframework.shell.standard.ShellMethod;
                import org.springframework.shell.standard.ShellOption;
                import org.springframework.stereotype.Component;

                import java.util.Map;
                import java.util.Set;

                @ShellComponent
                public class %s {

                    private final %s client;
                    private final ObjectMapper objectMapper;

                    public %s(%s client, ObjectMapper objectMapper) {
                        this.client = client;
                        this.objectMapper = objectMapper;
                    }
                %s
                    private String formatOutput(String json, String format) {
                        try {
                            Object parsed = objectMapper.readValue(json, Object.class);
                            return "table".equals(format)
                                    ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed)
                                    : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
                        } catch (Exception e) {
                            return json;
                        }
                    }
                %s}
                """.formatted(pkg, commandsClass, clientClass, commandsClass, clientClass,
                methodsBuilder, toolClassesBuilder);
    }

    public static String jbangScript(ProjectSpec spec) {
        String name = capitalize(spec.name());
        String prefix = spec.name().toUpperCase();
        String baseUrl = spec.api() != null && spec.api().baseUrl() != null
                ? spec.api().baseUrl() : "https://api.example.com";

        var commandMethods = new StringBuilder();
        for (var ep : spec.endpoints()) {
            commandMethods.append(jbangCommandMethod(spec.name(), ep));
        }

        return """
                ///usr/bin/env jbang "$0" "$@" ; exit $?
                //JAVA 21
                //DEPS org.springframework.boot:spring-boot-starter:3.5.6
                //DEPS org.springframework.shell:spring-shell-starter:3.4.0
                //DEPS org.springframework.boot:spring-boot-starter-web:3.5.6
                //DEPS io.jaiclaw:jaiclaw-core:0.1.0-SNAPSHOT

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.shell.standard.ShellComponent;
                import org.springframework.shell.standard.ShellMethod;
                import org.springframework.shell.standard.ShellOption;
                import org.springframework.stereotype.Component;
                import org.springframework.web.client.RestClient;
                import com.fasterxml.jackson.databind.ObjectMapper;

                @SpringBootApplication
                public class %sCli {
                    public static void main(String[] args) {
                        SpringApplication.run(%sCli.class, args);
                    }

                    @Component
                    static class ApiClient {
                        private final RestClient restClient;

                        ApiClient(@Value("${%s.api.base-url:%s}") String baseUrl) {
                            this.restClient = RestClient.builder().baseUrl(baseUrl).build();
                        }

                        String get(String path, Object... uriVars) {
                            return restClient.get().uri(path, uriVars).retrieve().body(String.class);
                        }

                        String post(String path, Object body) {
                            return restClient.post().uri(path).body(body).retrieve().body(String.class);
                        }
                    }

                    @ShellComponent
                    static class Commands {
                        private final ApiClient client;
                        private final ObjectMapper objectMapper = new ObjectMapper();

                        Commands(ApiClient client) {
                            this.client = client;
                        }
                %s    }
                }
                """.formatted(name, name, spec.name(), baseUrl, commandMethods);
    }

    // --- Private helpers ---

    private static String commandMethod(String prefix, EndpointSpec ep) {
        String methodName = toMethodName(ep);
        String commandKey = ep.commandKey() != null ? ep.commandKey() : deriveCommandKey(ep);

        var paramDecls = new StringBuilder();
        var callArgs = new StringBuilder();
        buildMethodParams(ep, paramDecls, callArgs);

        String clientCall = clientCallExpression(ep, callArgs.toString());

        return """

                    @ShellMethod(value = "%s", key = "%s %s")
                    public String %s(
                            %s@ShellOption(value = "--format", defaultValue = "json") String format) {
                        String raw = %s;
                        return formatOutput(raw, format);
                    }
                """.formatted(
                escapeJava(ep.summary() != null ? ep.summary() : commandKey),
                prefix, commandKey, methodName, paramDecls, clientCall);
    }

    private static String toolInnerClass(String prefix, String nameCapitalized, String commandsClass, EndpointSpec ep) {
        String methodName = toMethodName(ep);
        String toolName = prefix + "_" + methodName.replaceAll("([A-Z])", "_$1").toLowerCase();
        String toolClassName = capitalize(methodName) + "Tool";
        String commandKey = ep.commandKey() != null ? ep.commandKey() : deriveCommandKey(ep);

        var schemaProps = new StringBuilder();
        var requiredList = new StringBuilder();
        var callParams = new StringBuilder();
        buildToolParams(ep, schemaProps, requiredList, callParams);

        return """

                    @Component
                    public static class %s implements ToolCallback {
                        private final %s commands;

                        public %s(%s commands) {
                            this.commands = commands;
                        }

                        @Override
                        public ToolDefinition definition() {
                            return new ToolDefinition(
                                    "%s",
                                    "%s",
                                    "%s",
                                    \"\"\"
                                    {"type":"object","properties":{%s},"required":[%s]}
                                    \"\"\",
                                    Set.of(io.jaiclaw.core.tool.ToolProfile.FULL)
                            );
                        }

                        @Override
                        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
                            try {
                                String result = commands.%s(%s"json");
                                return new ToolResult.Success(result);
                            } catch (Exception e) {
                                return new ToolResult.Error("Failed to call %s: " + e.getMessage());
                            }
                        }
                    }
                """.formatted(
                toolClassName, commandsClass, toolClassName, commandsClass,
                toolName,
                escapeJava(ep.summary() != null ? ep.summary() : commandKey),
                prefix,
                schemaProps, requiredList,
                methodName, callParams,
                commandKey);
    }

    private static String jbangCommandMethod(String prefix, EndpointSpec ep) {
        String methodName = toMethodName(ep);
        String commandKey = ep.commandKey() != null ? ep.commandKey() : deriveCommandKey(ep);

        var paramDecls = new StringBuilder();
        var callArgs = new StringBuilder();
        buildMethodParams(ep, paramDecls, callArgs);

        String clientCall = clientCallExpression(ep, callArgs.toString());

        return """

                        @ShellMethod(value = "%s", key = "%s %s")
                        String %s(%s@ShellOption(value = "--format", defaultValue = "json") String format) {
                            try {
                                String raw = %s;
                                Object parsed = objectMapper.readValue(raw, Object.class);
                                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
                            } catch (Exception e) {
                                return "Error: " + e.getMessage();
                            }
                        }
                """.formatted(
                escapeJava(ep.summary() != null ? ep.summary() : commandKey),
                prefix, commandKey, methodName, paramDecls, clientCall);
    }

    private static void buildMethodParams(EndpointSpec ep, StringBuilder paramDecls, StringBuilder callArgs) {
        if (ep.params() != null) {
            for (var param : ep.params()) {
                if ("body".equals(param.in())) continue;
                String javaType = javaType(param.type());
                if (param.required()) {
                    paramDecls.append("""
                            @ShellOption("%s") %s %s,
                            """.formatted("--" + param.name(), javaType, param.name()).indent(8).stripTrailing() + "\n");
                } else {
                    paramDecls.append("""
                            @ShellOption(value = "%s", defaultValue = ShellOption.NULL) %s %s,
                            """.formatted("--" + param.name(), javaType, param.name()).indent(8).stripTrailing() + "\n");
                }
                callArgs.append(param.name()).append(", ");
            }
        }
    }

    private static void buildToolParams(EndpointSpec ep, StringBuilder schemaProps, StringBuilder requiredList, StringBuilder callParams) {
        if (ep.params() != null) {
            boolean first = true;
            boolean firstRequired = true;
            for (var param : ep.params()) {
                if ("body".equals(param.in())) continue;
                if (!first) schemaProps.append(",");
                first = false;
                schemaProps.append("""
                        "%s":{"type":"%s"}""".formatted(param.name(), jsonSchemaType(param.type())));
                if (param.required()) {
                    if (!firstRequired) requiredList.append(",");
                    firstRequired = false;
                    requiredList.append("\"%s\"".formatted(param.name()));
                }
                String cast = "integer".equals(param.type())
                        ? "parameters.get(\"%s\") != null ? ((Number) parameters.get(\"%s\")).intValue() : null".formatted(param.name(), param.name())
                        : "(String) parameters.get(\"%s\")".formatted(param.name());
                callParams.append(cast).append(", ");
            }
        }
    }

    private static String clientCallExpression(EndpointSpec ep, String callArgs) {
        String path = ep.path();
        // Convert {param} to %s for path params
        if (ep.params() != null) {
            for (var param : ep.params()) {
                if ("path".equals(param.in())) {
                    path = path.replace("{" + param.name() + "}", "{%s}".formatted(param.name()));
                }
            }
        }

        return switch (ep.method().toUpperCase()) {
            case "POST" -> "client.post(\"%s\", null)".formatted(path);
            case "PUT" -> "client.put(\"%s\", null%s)".formatted(path, callArgs.isEmpty() ? "" : ", " + callArgs.substring(0, callArgs.length() - 2));
            case "DELETE" -> "client.delete(\"%s\"%s)".formatted(path, callArgs.isEmpty() ? "" : ", " + callArgs.substring(0, callArgs.length() - 2));
            default -> "client.get(\"%s\"%s)".formatted(path, callArgs.isEmpty() ? "" : ", " + callArgs.substring(0, callArgs.length() - 2));
        };
    }

    private static String authClientSetup(AuthConfig auth, String prefix) {
        if (auth == null || "none".equals(auth.type())) return "";
        return switch (auth.type()) {
            case "header" -> """

                        builder.defaultHeader("%s", %sapiKey);
                """.formatted(
                    auth.headerName(),
                    auth.headerValuePrefix() != null && !auth.headerValuePrefix().isEmpty()
                            ? "\"%s\" + ".formatted(auth.headerValuePrefix()) : "");
            case "basic" -> """

                        builder.defaultHeaders(h -> h.setBasicAuth(username, password));
                """;
            case "oauth2" -> """

                        // OAuth2 client credentials — token refresh handled by interceptor
                        builder.requestInterceptor(new OAuth2Interceptor(tokenUrl, clientId, clientSecret, "%s"));
                """.formatted(String.join(" ", auth.scopes()));
            default -> "";
        };
    }

    private static String authConstructorParams(AuthConfig auth, String prefix) {
        if (auth == null || "none".equals(auth.type())) return "";
        return switch (auth.type()) {
            case "header" -> ",\n            @Value(\"${%s}\") String apiKey".formatted(
                    auth.envVar() != null ? auth.envVar() : prefix + "_API_KEY");
            case "basic" -> """
                    ,
                            @Value("${%s}") String username,
                            @Value("${%s}") String password""".formatted(
                    auth.usernameEnv() != null ? auth.usernameEnv() : prefix + "_USERNAME",
                    auth.passwordEnv() != null ? auth.passwordEnv() : prefix + "_PASSWORD");
            case "oauth2" -> """
                    ,
                            @Value("${%s}") String tokenUrl,
                            @Value("${%s}") String clientId,
                            @Value("${%s}") String clientSecret""".formatted(
                    "auth.tokenUrl", // Will be in application.yml
                    auth.clientIdEnv() != null ? auth.clientIdEnv() : prefix + "_CLIENT_ID",
                    auth.clientSecretEnv() != null ? auth.clientSecretEnv() : prefix + "_CLIENT_SECRET");
            default -> "";
        };
    }

    static String toMethodName(EndpointSpec ep) {
        if (ep.operationId() != null && !ep.operationId().isBlank()) {
            return ep.operationId();
        }
        if (ep.commandKey() != null && !ep.commandKey().isBlank()) {
            return toCamelCase(ep.commandKey());
        }
        // Derive from method + last path segment
        String lastSegment = ep.path().replaceAll("\\{[^}]+}", "").replaceAll("/+$", "");
        int lastSlash = lastSegment.lastIndexOf('/');
        if (lastSlash >= 0) lastSegment = lastSegment.substring(lastSlash + 1);
        return toCamelCase(ep.method().toLowerCase() + "-" + lastSegment);
    }

    static String deriveCommandKey(EndpointSpec ep) {
        if (ep.commandKey() != null) return ep.commandKey();
        if (ep.operationId() != null) {
            return ep.operationId().replaceAll("([A-Z])", "-$1").toLowerCase().replaceFirst("^-", "");
        }
        String lastSegment = ep.path().replaceAll("\\{[^}]+}", "").replaceAll("/+$", "");
        int lastSlash = lastSegment.lastIndexOf('/');
        if (lastSlash >= 0) lastSegment = lastSegment.substring(lastSlash + 1);
        return ep.method().toLowerCase() + "-" + lastSegment;
    }

    private static String toCamelCase(String kebab) {
        String[] parts = kebab.split("-");
        var sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(parts[i].substring(0, 1).toUpperCase()).append(parts[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static String resolvePackage(ProjectSpec spec) {
        if (spec.packageName() != null && !spec.packageName().isBlank()) return spec.packageName();
        if (spec.groupId() != null) return spec.groupId() + ".cli." + spec.name();
        return "io.jaiclaw.cli." + spec.name();
    }

    private static String javaType(String type) {
        return switch (type != null ? type : "string") {
            case "integer", "int" -> "Integer";
            case "boolean" -> "Boolean";
            case "number" -> "Double";
            default -> "String";
        };
    }

    private static String jsonSchemaType(String type) {
        return switch (type != null ? type : "string") {
            case "integer", "int" -> "integer";
            case "boolean" -> "boolean";
            case "number" -> "number";
            default -> "string";
        };
    }

    private static String escapeJava(String s) {
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
