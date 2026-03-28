package io.jaiclaw.cli.architect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Specification for a single API endpoint to wrap as a CLI command.
 *
 * @param method      HTTP method (GET, POST, PUT, DELETE)
 * @param path        URL path template (e.g. "/users/{id}")
 * @param operationId Unique operation identifier from OpenAPI
 * @param summary     Human-readable description
 * @param commandKey  CLI command suffix (e.g. "list-users")
 * @param tag         Grouping tag for the endpoint
 * @param params      Parameter definitions
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EndpointSpec(
        String method,
        String path,
        String operationId,
        String summary,
        String commandKey,
        String tag,
        List<ParamSpec> params
) {
    public EndpointSpec {
        if (params == null) params = List.of();
    }

    /**
     * A single parameter for an endpoint.
     *
     * @param name     Parameter name
     * @param type     Data type (string, integer, boolean, etc.)
     * @param in       Location: path, query, header, body
     * @param required Whether the parameter is required
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParamSpec(
            String name,
            String type,
            String in,
            boolean required
    ) {
        public ParamSpec {
            if (type == null) type = "string";
            if (in == null) in = "query";
        }
    

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String name;
            private String type;
            private String in;
            private boolean required;

            public Builder name(String name) { this.name = name; return this; }
            public Builder type(String type) { this.type = type; return this; }
            public Builder in(String in) { this.in = in; return this; }
            public Builder required(boolean required) { this.required = required; return this; }

            public ParamSpec build() {
                return new ParamSpec(name, type, in, required);
            }
        }
}

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String method;
        private String path;
        private String operationId;
        private String summary;
        private String commandKey;
        private String tag;
        private List<ParamSpec> params;

        public Builder method(String method) { this.method = method; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder operationId(String operationId) { this.operationId = operationId; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder commandKey(String commandKey) { this.commandKey = commandKey; return this; }
        public Builder tag(String tag) { this.tag = tag; return this; }
        public Builder params(List<ParamSpec> params) { this.params = params; return this; }

        public EndpointSpec build() {
            return new EndpointSpec(method, path, operationId, summary, commandKey, tag, params);
        }
    }
}
