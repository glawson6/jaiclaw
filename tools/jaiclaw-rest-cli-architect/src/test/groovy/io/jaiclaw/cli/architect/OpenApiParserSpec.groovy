package io.jaiclaw.cli.architect

import spock.lang.Specification

class OpenApiParserSpec extends Specification {

    def "parses OpenAPI 3.x spec with endpoints and auth"() {
        given:
        def spec = '''
        {
          "openapi": "3.0.0",
          "info": { "title": "Acme API", "version": "1.0" },
          "servers": [{ "url": "https://api.acme.com/v1" }],
          "paths": {
            "/users": {
              "get": {
                "operationId": "listUsers",
                "summary": "List all users",
                "tags": ["Users"],
                "parameters": [
                  { "name": "limit", "in": "query", "schema": { "type": "integer" } }
                ]
              }
            },
            "/users/{id}": {
              "get": {
                "operationId": "getUser",
                "summary": "Get user by ID",
                "tags": ["Users"],
                "parameters": [
                  { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
                ]
              }
            }
          },
          "components": {
            "securitySchemes": {
              "apiKey": {
                "type": "apiKey",
                "name": "X-API-Key",
                "in": "header"
              }
            }
          }
        }
        '''

        when:
        def result = OpenApiParser.parse(spec)

        then:
        result.title() == "Acme API"
        result.baseUrl() == "https://api.acme.com/v1"
        result.endpoints().size() == 2
        result.endpoints()[0].operationId() == "listUsers"
        result.endpoints()[0].summary() == "List all users"
        result.endpoints()[0].tag() == "Users"
        result.endpoints()[0].params().size() == 1
        result.endpoints()[0].params()[0].name() == "limit"
        result.endpoints()[0].params()[0].type() == "integer"
        result.endpoints()[1].operationId() == "getUser"
        result.endpoints()[1].params()[0].required()
        result.auth().type() == "header"
        result.auth().headerName() == "X-API-Key"
    }

    def "parses Swagger 2.x spec"() {
        given:
        def spec = '''
        {
          "swagger": "2.0",
          "info": { "title": "Legacy API", "version": "1.0" },
          "host": "api.legacy.com",
          "basePath": "/v2",
          "schemes": ["https"],
          "paths": {
            "/items": {
              "get": {
                "operationId": "listItems",
                "summary": "List items",
                "parameters": [
                  { "name": "page", "in": "query", "type": "integer" }
                ]
              }
            }
          },
          "securityDefinitions": {
            "basicAuth": { "type": "basic" }
          }
        }
        '''

        when:
        def result = OpenApiParser.parse(spec)

        then:
        result.title() == "Legacy API"
        result.baseUrl() == "https://api.legacy.com/v2"
        result.endpoints().size() == 1
        result.endpoints()[0].operationId() == "listItems"
        result.auth().type() == "basic"
    }

    def "parses bearer token auth"() {
        given:
        def spec = '''
        {
          "openapi": "3.0.0",
          "info": { "title": "Bearer API" },
          "paths": {},
          "components": {
            "securitySchemes": {
              "bearerAuth": {
                "type": "http",
                "scheme": "bearer"
              }
            }
          }
        }
        '''

        when:
        def result = OpenApiParser.parse(spec)

        then:
        result.auth().type() == "header"
        result.auth().headerName() == "Authorization"
        result.auth().headerValuePrefix() == "Bearer "
    }

    def "parses OAuth2 client credentials"() {
        given:
        def spec = '''
        {
          "openapi": "3.0.0",
          "info": { "title": "OAuth API" },
          "paths": {},
          "components": {
            "securitySchemes": {
              "oauth": {
                "type": "oauth2",
                "flows": {
                  "clientCredentials": {
                    "tokenUrl": "https://auth.example.com/token"
                  }
                }
              }
            }
          }
        }
        '''

        when:
        def result = OpenApiParser.parse(spec)

        then:
        result.auth().type() == "oauth2"
        result.auth().tokenUrl() == "https://auth.example.com/token"
    }

    def "returns NONE auth when no security schemes"() {
        given:
        def spec = '''
        {
          "openapi": "3.0.0",
          "info": { "title": "No Auth API" },
          "paths": {}
        }
        '''

        when:
        def result = OpenApiParser.parse(spec)

        then:
        result.auth().type() == "none"
    }

    def "throws on invalid JSON"() {
        when:
        OpenApiParser.parse("not json")

        then:
        thrown(IllegalArgumentException)
    }
}
