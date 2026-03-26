package io.jaiclaw.cli.architect.templates;

import io.jaiclaw.cli.architect.AuthConfig;
import io.jaiclaw.cli.architect.ProjectSpec;

/**
 * Text block templates for application.yml and .env.example files.
 */
public final class ConfigTemplates {

    private ConfigTemplates() {}

    public static String applicationYml(ProjectSpec spec) {
        String prefix = spec.name();
        String prefixUpper = prefix.toUpperCase();
        String baseUrl = spec.api() != null && spec.api().baseUrl() != null
                ? spec.api().baseUrl() : "https://api.example.com";

        var authConfig = authYml(spec.auth(), prefixUpper);

        return """
                spring:
                  shell:
                    interactive:
                      enabled: true
                    script:
                      enabled: true
                  main:
                    banner-mode: "off"

                %s:
                  api:
                    base-url: ${%s_BASE_URL:%s}
                %s""".formatted(prefix, prefixUpper, baseUrl, authConfig);
    }

    public static String envExample(ProjectSpec spec) {
        String prefix = spec.name().toUpperCase();
        String baseUrl = spec.api() != null && spec.api().baseUrl() != null
                ? spec.api().baseUrl() : "https://api.example.com";

        var sb = new StringBuilder();
        sb.append("# %s CLI — Environment Variables\n".formatted(
                spec.api() != null && spec.api().title() != null ? spec.api().title() : capitalize(spec.name())));
        sb.append("%s_BASE_URL=%s\n".formatted(prefix, baseUrl));

        if (spec.auth() != null) {
            switch (spec.auth().type()) {
                case "header" -> {
                    String envVar = spec.auth().envVar() != null ? spec.auth().envVar() : prefix + "_API_KEY";
                    sb.append("%s=your-api-key-here\n".formatted(envVar));
                }
                case "basic" -> {
                    sb.append("%s=your-username\n".formatted(
                            spec.auth().usernameEnv() != null ? spec.auth().usernameEnv() : prefix + "_USERNAME"));
                    sb.append("%s=your-password\n".formatted(
                            spec.auth().passwordEnv() != null ? spec.auth().passwordEnv() : prefix + "_PASSWORD"));
                }
                case "oauth2" -> {
                    sb.append("%s=https://auth.example.com/oauth/token\n".formatted(prefix + "_TOKEN_URL"));
                    sb.append("%s=your-client-id\n".formatted(
                            spec.auth().clientIdEnv() != null ? spec.auth().clientIdEnv() : prefix + "_CLIENT_ID"));
                    sb.append("%s=your-client-secret\n".formatted(
                            spec.auth().clientSecretEnv() != null ? spec.auth().clientSecretEnv() : prefix + "_CLIENT_SECRET"));
                }
                default -> { /* none */ }
            }
        }

        return sb.toString();
    }

    public static String spockTestStub(ProjectSpec spec) {
        String pkg = JavaTemplates.resolvePackage(spec);
        String name = capitalize(spec.name());
        String clientClass = name + "ApiClient";
        String commandsClass = name + "Commands";

        return """
                package %s

                import spock.lang.Specification
                import com.fasterxml.jackson.databind.ObjectMapper

                class %sSpec extends Specification {

                    def client = Mock(%s)
                    def objectMapper = new ObjectMapper()
                    def commands = new %s(client, objectMapper)

                    def "commands class is instantiated"() {
                        expect:
                        commands != null
                    }
                }
                """.formatted(pkg, commandsClass, clientClass, commandsClass);
    }

    private static String authYml(AuthConfig auth, String prefix) {
        if (auth == null || "none".equals(auth.type())) return "";
        return switch (auth.type()) {
            case "header" -> "    key: ${%s:}\n".formatted(
                    auth.envVar() != null ? auth.envVar() : prefix + "_API_KEY");
            case "basic" -> """
                        username: ${%s:}
                        password: ${%s:}
                    """.formatted(
                    auth.usernameEnv() != null ? auth.usernameEnv() : prefix + "_USERNAME",
                    auth.passwordEnv() != null ? auth.passwordEnv() : prefix + "_PASSWORD");
            case "oauth2" -> """
                        oauth2:
                          token-url: ${%s_TOKEN_URL:}
                          client-id: ${%s:}
                          client-secret: ${%s:}
                          scopes: ${%s_SCOPES:}
                    """.formatted(prefix,
                    auth.clientIdEnv() != null ? auth.clientIdEnv() : prefix + "_CLIENT_ID",
                    auth.clientSecretEnv() != null ? auth.clientSecretEnv() : prefix + "_CLIENT_SECRET",
                    prefix);
            default -> "";
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
