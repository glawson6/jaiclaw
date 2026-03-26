package io.jaiclaw.cli.architect

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class JRestCliCommandsSpec extends Specification {

    @TempDir
    Path tempDir

    def commands = new JRestCliCommands()

    def "scaffold generates files from valid spec"() {
        given:
        def specJson = '''
        {
          "name": "acme",
          "mode": "STANDALONE",
          "outputDir": "%s",
          "groupId": "com.example",
          "packageName": "com.example.cli.acme",
          "api": {
            "title": "Acme API",
            "baseUrl": "https://api.acme.com/v1"
          },
          "auth": { "type": "none" },
          "endpoints": [
            {
              "method": "GET",
              "path": "/users",
              "operationId": "listUsers",
              "summary": "List all users",
              "commandKey": "list-users"
            }
          ]
        }
        '''.formatted(tempDir.resolve("output").toString().replace("\\\\", "/"))

        def specFile = tempDir.resolve("test-spec.json")
        Files.writeString(specFile, specJson)

        when:
        def result = commands.scaffold(specFile.toString(), false)

        then:
        result.contains("Generated")
        Files.exists(tempDir.resolve("output/pom.xml"))
        Files.exists(tempDir.resolve("output/src/main/resources/application.yml"))
    }

    def "scaffold rejects invalid spec without --fill"() {
        given:
        def specJson = '{"api": {}}'
        def specFile = tempDir.resolve("bad-spec.json")
        Files.writeString(specFile, specJson)

        when:
        def result = commands.scaffold(specFile.toString(), false)

        then:
        result.contains("validation failed")
    }

    def "validate reports issues on invalid spec"() {
        given:
        def specJson = '{"api": {}}'
        def specFile = tempDir.resolve("bad-spec.json")
        Files.writeString(specFile, specJson)

        when:
        def result = commands.validate(specFile.toString())

        then:
        result.contains("Validation issues")
        result.contains("name")
    }

    def "validate passes on valid spec"() {
        given:
        def specJson = '''
        {
          "name": "test",
          "mode": "JAICLAW_SUBMODULE",
          "api": { "baseUrl": "https://api.example.com" },
          "endpoints": [{ "method": "GET", "path": "/test" }]
        }
        '''
        def specFile = tempDir.resolve("ok-spec.json")
        Files.writeString(specFile, specJson)

        when:
        def result = commands.validate(specFile.toString())

        then:
        result.contains("valid")
    }

    def "from-openapi parses spec and returns JSON"() {
        given:
        def openapiJson = '''
        {
          "openapi": "3.0.0",
          "info": { "title": "Test API" },
          "servers": [{ "url": "https://api.test.com" }],
          "paths": {
            "/items": {
              "get": { "operationId": "listItems", "summary": "List items" }
            }
          }
        }
        '''
        def specFile = tempDir.resolve("openapi.json")
        Files.writeString(specFile, openapiJson)

        when:
        def result = commands.fromOpenapi(specFile.toString(), null)

        then:
        result.contains("Test API")
        result.contains("listItems")
        result.contains("https://api.test.com")
    }

    def "from-openapi writes to output file when specified"() {
        given:
        def openapiJson = '''
        {
          "openapi": "3.0.0",
          "info": { "title": "Test API" },
          "servers": [{ "url": "https://api.test.com" }],
          "paths": {
            "/items": {
              "get": { "operationId": "listItems", "summary": "List items" }
            }
          }
        }
        '''
        def specFile = tempDir.resolve("openapi.json")
        Files.writeString(specFile, openapiJson)
        def outputFile = tempDir.resolve("generated-spec.json")

        when:
        def result = commands.fromOpenapi(specFile.toString(), outputFile.toString())

        then:
        result.contains("Spec template written to")
        Files.exists(outputFile)
        Files.readString(outputFile).contains("Test API")
    }
}
