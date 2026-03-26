package io.jaiclaw.cli.architect;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link ProjectSpec} and reports missing or invalid fields.
 * Used by the {@code --fill} flag to tell the LLM what to ask the user about.
 */
public final class SpecValidator {

    private SpecValidator() {}

    /**
     * Validate the spec and return a list of issues. Empty list means valid.
     */
    public static List<String> validate(ProjectSpec spec) {
        var issues = new ArrayList<String>();

        if (isBlank(spec.name())) {
            issues.add("name: required — short name for CLI prefix and module (e.g. 'acme')");
        }

        if (spec.mode() == null) {
            issues.add("mode: required — one of JAICLAW_SUBMODULE, EXTERNAL_SUBMODULE, STANDALONE, JBANG");
        }

        if (isBlank(spec.outputDir()) && spec.mode() != ProjectMode.JAICLAW_SUBMODULE) {
            issues.add("outputDir: required for non-JAICLAW_SUBMODULE modes");
        }

        if (isBlank(spec.groupId()) && spec.mode() != ProjectMode.JAICLAW_SUBMODULE) {
            issues.add("groupId: required for non-JAICLAW_SUBMODULE modes (e.g. 'com.example')");
        }

        // API section
        if (spec.api() == null) {
            issues.add("api: required — API metadata section");
        } else {
            if (isBlank(spec.api().baseUrl()) && isBlank(spec.api().openapiSpec())) {
                issues.add("api.baseUrl or api.openapiSpec: at least one required");
            }
        }

        // Auth
        if (spec.auth() != null && !"none".equals(spec.auth().type())) {
            validateAuth(spec.auth(), issues);
        }

        // Endpoints
        if ((spec.endpoints() == null || spec.endpoints().isEmpty())
                && (spec.api() == null || isBlank(spec.api().openapiSpec()))) {
            issues.add("endpoints: required when api.openapiSpec is not provided");
        }

        if (spec.endpoints() != null) {
            for (int i = 0; i < spec.endpoints().size(); i++) {
                validateEndpoint(spec.endpoints().get(i), i, issues);
            }
        }

        return issues;
    }

    /**
     * Returns fields that are missing but could be filled by an LLM.
     */
    public static List<String> findGaps(ProjectSpec spec) {
        var gaps = new ArrayList<String>();

        if (isBlank(spec.name())) gaps.add("name");
        if (isBlank(spec.packageName())) gaps.add("packageName");
        if (spec.api() != null && isBlank(spec.api().title())) gaps.add("api.title");

        if (spec.endpoints() != null) {
            for (int i = 0; i < spec.endpoints().size(); i++) {
                var ep = spec.endpoints().get(i);
                if (isBlank(ep.commandKey())) gaps.add("endpoints[%d].commandKey".formatted(i));
                if (isBlank(ep.summary())) gaps.add("endpoints[%d].summary".formatted(i));
            }
        }

        return gaps;
    }

    private static void validateAuth(AuthConfig auth, List<String> issues) {
        switch (auth.type()) {
            case "header" -> {
                if (isBlank(auth.headerName())) issues.add("auth.headerName: required for header auth");
                if (isBlank(auth.envVar())) issues.add("auth.envVar: required for header auth");
            }
            case "basic" -> {
                if (isBlank(auth.usernameEnv())) issues.add("auth.usernameEnv: required for basic auth");
                if (isBlank(auth.passwordEnv())) issues.add("auth.passwordEnv: required for basic auth");
            }
            case "oauth2" -> {
                if (isBlank(auth.tokenUrl())) issues.add("auth.tokenUrl: required for oauth2 auth");
                if (isBlank(auth.clientIdEnv())) issues.add("auth.clientIdEnv: required for oauth2 auth");
                if (isBlank(auth.clientSecretEnv())) issues.add("auth.clientSecretEnv: required for oauth2 auth");
            }
            case "none" -> { /* no validation needed */ }
            default -> issues.add("auth.type: unknown type '%s' — must be none, header, basic, or oauth2".formatted(auth.type()));
        }
    }

    private static void validateEndpoint(EndpointSpec ep, int index, List<String> issues) {
        String prefix = "endpoints[%d]".formatted(index);
        if (isBlank(ep.method())) issues.add(prefix + ".method: required");
        if (isBlank(ep.path())) issues.add(prefix + ".path: required");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
