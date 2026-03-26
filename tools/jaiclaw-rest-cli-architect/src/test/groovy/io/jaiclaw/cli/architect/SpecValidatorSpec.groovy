package io.jaiclaw.cli.architect

import spock.lang.Specification

class SpecValidatorSpec extends Specification {

    def "valid standalone spec passes validation"() {
        given:
        def spec = new ProjectSpec(
                "acme", ProjectMode.STANDALONE, "/tmp/acme", "com.example",
                "com.example.cli.acme", "both",
                new ProjectSpec.ApiSpec("Acme API", "https://api.acme.com", null),
                new AuthConfig("header", "X-API-Key", "", "ACME_API_KEY", null, null, null, null, null, []),
                [new EndpointSpec("GET", "/users", "listUsers", "List users", "list-users", null, [])]
        )

        when:
        def issues = SpecValidator.validate(spec)

        then:
        issues.isEmpty()
    }

    def "missing name is reported"() {
        given:
        def spec = new ProjectSpec(
                null, ProjectMode.STANDALONE, "/tmp/out", "com.example",
                null, null,
                new ProjectSpec.ApiSpec("API", "https://api.example.com", null),
                AuthConfig.NONE, [new EndpointSpec("GET", "/test", null, null, null, null, [])]
        )

        when:
        def issues = SpecValidator.validate(spec)

        then:
        issues.any { it.contains("name") }
    }

    def "missing outputDir for STANDALONE is reported"() {
        given:
        def spec = new ProjectSpec(
                "test", ProjectMode.STANDALONE, null, "com.example",
                null, null,
                new ProjectSpec.ApiSpec("API", "https://api.example.com", null),
                AuthConfig.NONE, [new EndpointSpec("GET", "/test", null, null, null, null, [])]
        )

        when:
        def issues = SpecValidator.validate(spec)

        then:
        issues.any { it.contains("outputDir") }
    }

    def "JAICLAW_SUBMODULE does not require outputDir or groupId"() {
        given:
        def spec = new ProjectSpec(
                "test", ProjectMode.JAICLAW_SUBMODULE, null, null,
                null, null,
                new ProjectSpec.ApiSpec("API", "https://api.example.com", null),
                AuthConfig.NONE, [new EndpointSpec("GET", "/test", null, null, null, null, [])]
        )

        when:
        def issues = SpecValidator.validate(spec)

        then:
        !issues.any { it.contains("outputDir") }
        !issues.any { it.contains("groupId") }
    }

    def "missing endpoints without openapi spec is reported"() {
        given:
        def spec = new ProjectSpec(
                "test", ProjectMode.STANDALONE, "/tmp/out", "com.example",
                null, null,
                new ProjectSpec.ApiSpec("API", "https://api.example.com", null),
                AuthConfig.NONE, []
        )

        when:
        def issues = SpecValidator.validate(spec)

        then:
        issues.any { it.contains("endpoints") }
    }

    def "header auth requires headerName and envVar"() {
        given:
        def spec = new ProjectSpec(
                "test", ProjectMode.STANDALONE, "/tmp/out", "com.example",
                null, null,
                new ProjectSpec.ApiSpec("API", "https://api.example.com", null),
                new AuthConfig("header", null, null, null, null, null, null, null, null, []),
                [new EndpointSpec("GET", "/test", null, null, null, null, [])]
        )

        when:
        def issues = SpecValidator.validate(spec)

        then:
        issues.any { it.contains("headerName") }
        issues.any { it.contains("envVar") }
    }

    def "oauth2 auth requires tokenUrl, clientIdEnv, clientSecretEnv"() {
        given:
        def spec = new ProjectSpec(
                "test", ProjectMode.STANDALONE, "/tmp/out", "com.example",
                null, null,
                new ProjectSpec.ApiSpec("API", "https://api.example.com", null),
                new AuthConfig("oauth2", null, null, null, null, null, null, null, null, []),
                [new EndpointSpec("GET", "/test", null, null, null, null, [])]
        )

        when:
        def issues = SpecValidator.validate(spec)

        then:
        issues.any { it.contains("tokenUrl") }
        issues.any { it.contains("clientIdEnv") }
        issues.any { it.contains("clientSecretEnv") }
    }

    def "findGaps identifies missing optional fields"() {
        given:
        def spec = new ProjectSpec(
                null, ProjectMode.STANDALONE, "/tmp/out", "com.example",
                null, null,
                new ProjectSpec.ApiSpec(null, "https://api.example.com", null),
                AuthConfig.NONE,
                [new EndpointSpec("GET", "/users", null, null, null, null, [])]
        )

        when:
        def gaps = SpecValidator.findGaps(spec)

        then:
        gaps.contains("name")
        gaps.contains("packageName")
        gaps.contains("api.title")
        gaps.any { it.contains("commandKey") }
    }
}
