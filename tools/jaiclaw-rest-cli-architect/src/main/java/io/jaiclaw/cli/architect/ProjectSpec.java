package io.jaiclaw.cli.architect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Full specification for a CLI project to scaffold. Deserialized from the input JSON spec file.
 *
 * @param name        Short name used as CLI command prefix and module suffix (e.g. "acme")
 * @param mode        Project structure mode
 * @param outputDir   Directory where the project should be generated
 * @param groupId     Maven groupId for generated project
 * @param packageName Java package name (e.g. "com.example.cli.acme")
 * @param outputFormat Response formatting: "json", "table", or "both"
 * @param api         API metadata
 * @param auth        Authentication configuration
 * @param endpoints   List of endpoints to wrap
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectSpec(
        String name,
        ProjectMode mode,
        String outputDir,
        String groupId,
        String packageName,
        String outputFormat,
        ApiSpec api,
        AuthConfig auth,
        List<EndpointSpec> endpoints
) {
    public ProjectSpec {
        if (mode == null) mode = ProjectMode.STANDALONE;
        if (outputFormat == null) outputFormat = "both";
        if (auth == null) auth = AuthConfig.NONE;
        if (endpoints == null) endpoints = List.of();
    }

    /**
     * API metadata.
     *
     * @param title       Display name for the API
     * @param baseUrl     Base URL (e.g. "https://api.acme.com/v1")
     * @param openapiSpec URL or file path to an OpenAPI spec
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiSpec(
            String title,
            String baseUrl,
            String openapiSpec
    ) {}

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private ProjectMode mode;
        private String outputDir;
        private String groupId;
        private String packageName;
        private String outputFormat;
        private ApiSpec api;
        private AuthConfig auth;
        private List<EndpointSpec> endpoints;

        public Builder name(String name) { this.name = name; return this; }
        public Builder mode(ProjectMode mode) { this.mode = mode; return this; }
        public Builder outputDir(String outputDir) { this.outputDir = outputDir; return this; }
        public Builder groupId(String groupId) { this.groupId = groupId; return this; }
        public Builder packageName(String packageName) { this.packageName = packageName; return this; }
        public Builder outputFormat(String outputFormat) { this.outputFormat = outputFormat; return this; }
        public Builder api(ApiSpec api) { this.api = api; return this; }
        public Builder auth(AuthConfig auth) { this.auth = auth; return this; }
        public Builder endpoints(List<EndpointSpec> endpoints) { this.endpoints = endpoints; return this; }

        public ProjectSpec build() {
            return new ProjectSpec(
                    name, mode, outputDir, groupId, packageName, outputFormat, api, auth, endpoints);
        }
    }
}
