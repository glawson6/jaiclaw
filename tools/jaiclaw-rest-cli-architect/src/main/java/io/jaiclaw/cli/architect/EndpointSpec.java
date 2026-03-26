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
    }
}
