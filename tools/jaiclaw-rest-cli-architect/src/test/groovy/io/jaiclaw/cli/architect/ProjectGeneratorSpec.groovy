package io.jaiclaw.cli.architect

import spock.lang.Specification

class ProjectGeneratorSpec extends Specification {

    def "generates standalone project files"() {
        given:
        def spec = new ProjectSpec(
                "acme", ProjectMode.STANDALONE, "/tmp/acme", "com.example",
                "com.example.cli.acme", "both",
                new ProjectSpec.ApiSpec("Acme CRM API", "https://api.acme.com/v1", null),
                new AuthConfig("header", "X-API-Key", "", "ACME_API_KEY", null, null, null, null, null, []),
                [
                    new EndpointSpec("GET", "/users", "listUsers", "List all users", "list-users", null, []),
                    new EndpointSpec("GET", "/users/{id}", "getUser", "Get user by ID", "get-user", null,
                            [new EndpointSpec.ParamSpec("id", "string", "path", true)])
                ]
        )

        when:
        def files = ProjectGenerator.generate(spec)

        then:
        files.containsKey("pom.xml")
        files.containsKey("src/main/java/com/example/cli/acme/AcmeCliApplication.java")
        files.containsKey("src/main/java/com/example/cli/acme/AcmeApiClient.java")
        files.containsKey("src/main/java/com/example/cli/acme/AcmeCommands.java")
        files.containsKey("src/main/resources/application.yml")
        files.containsKey(".env.example")
        files.containsKey("src/test/groovy/com/example/cli/acme/AcmeCommandsSpec.groovy")
    }

    def "standalone pom.xml has spring-boot-starter-parent"() {
        given:
        def spec = new ProjectSpec(
                "acme", ProjectMode.STANDALONE, "/tmp/acme", "com.example",
                "com.example.cli.acme", "both",
                new ProjectSpec.ApiSpec("Acme API", "https://api.acme.com", null),
                AuthConfig.NONE,
                [new EndpointSpec("GET", "/test", "listTest", "Test", "list-test", null, [])]
        )

        when:
        def files = ProjectGenerator.generate(spec)

        then:
        files["pom.xml"].contains("spring-boot-starter-parent")
        files["pom.xml"].contains("com.example")
    }

    def "jaiclaw submodule pom.xml has jaiclaw-parent"() {
        given:
        def spec = new ProjectSpec(
                "acme", ProjectMode.JAICLAW_SUBMODULE, null, null,
                null, null,
                new ProjectSpec.ApiSpec("Acme API", "https://api.acme.com", null),
                AuthConfig.NONE,
                [new EndpointSpec("GET", "/test", "listTest", "Test", "list-test", null, [])]
        )

        when:
        def files = ProjectGenerator.generate(spec)

        then:
        files["pom.xml"].contains("jaiclaw-parent")
        files["pom.xml"].contains("jaiclaw-cli-acme")
    }

    def "JBang mode generates single file"() {
        given:
        def spec = new ProjectSpec(
                "acme", ProjectMode.JBANG, null, null,
                null, null,
                new ProjectSpec.ApiSpec("Acme API", "https://api.acme.com", null),
                AuthConfig.NONE,
                [new EndpointSpec("GET", "/users", "listUsers", "List users", "list-users", null, [])]
        )

        when:
        def files = ProjectGenerator.generate(spec)

        then:
        files.size() == 1
        files.containsKey("AcmeCli.java")
        files["AcmeCli.java"].contains("///usr/bin/env jbang")
        files["AcmeCli.java"].contains("//JAVA 21")
    }

    def "generated commands contain ShellMethod annotations"() {
        given:
        def spec = new ProjectSpec(
                "acme", ProjectMode.STANDALONE, "/tmp/acme", "com.example",
                "com.example.cli.acme", "both",
                new ProjectSpec.ApiSpec("Acme API", "https://api.acme.com/v1", null),
                AuthConfig.NONE,
                [new EndpointSpec("GET", "/users", "listUsers", "List all users", "list-users", null, [])]
        )

        when:
        def files = ProjectGenerator.generate(spec)
        def commands = files["src/main/java/com/example/cli/acme/AcmeCommands.java"]

        then:
        commands.contains("@ShellComponent")
        commands.contains("@ShellMethod")
        commands.contains("acme list-users")
        commands.contains("implements ToolCallback")
    }

    def "env example contains auth vars for header auth"() {
        given:
        def spec = new ProjectSpec(
                "acme", ProjectMode.STANDALONE, "/tmp/acme", "com.example",
                "com.example.cli.acme", "both",
                new ProjectSpec.ApiSpec("Acme API", "https://api.acme.com", null),
                new AuthConfig("header", "X-API-Key", "", "ACME_API_KEY", null, null, null, null, null, []),
                [new EndpointSpec("GET", "/test", null, null, null, null, [])]
        )

        when:
        def files = ProjectGenerator.generate(spec)

        then:
        files[".env.example"].contains("ACME_API_KEY")
        files[".env.example"].contains("ACME_BASE_URL")
    }

    def "application yml contains base URL config"() {
        given:
        def spec = new ProjectSpec(
                "acme", ProjectMode.STANDALONE, "/tmp/acme", "com.example",
                "com.example.cli.acme", "both",
                new ProjectSpec.ApiSpec("Acme API", "https://api.acme.com/v1", null),
                AuthConfig.NONE,
                [new EndpointSpec("GET", "/test", null, null, null, null, [])]
        )

        when:
        def files = ProjectGenerator.generate(spec)

        then:
        files["src/main/resources/application.yml"].contains("base-url")
        files["src/main/resources/application.yml"].contains("https://api.acme.com/v1")
    }
}
