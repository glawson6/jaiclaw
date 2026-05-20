package io.jaiclaw.rules.engine.loader

import org.kie.api.KieServices
import org.kie.api.builder.KieFileSystem
import spock.lang.Specification
import spock.lang.Subject

/**
 * Comprehensive Spock tests for UrlRuleLoader.
 * Tests cover:
 * - Configuration validation
 * - URL format validation
 * - Security validation (SSRF prevention)
 * - Error handling
 * - Timeout configuration
 * - Authentication headers
 *
 * Note: Most tests focus on validation and configuration since actual HTTP
 * requests would require a mock server or external dependencies.
 */
class UrlRuleLoaderSpec extends Specification {

    def kieServices = KieServices.Factory.get()
    def kieFileSystem

    def setup() {
        kieFileSystem = kieServices.newKieFileSystem()
    }

    // ========== Configuration and Basic Tests ==========

    def "should create loader with valid HTTPS URL"() {
        given: "a loader with valid HTTPS URL"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "loader is created successfully"
        loader.loaderType == "url"
        loader.locations == locations
    }

    def "should create loader with valid HTTP URL"() {
        given: "a loader with valid HTTP URL"
        def locations = ["http://example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "loader is created successfully"
        loader.loaderType == "url"
        loader.locations == locations
    }

    def "should create loader with multiple URLs"() {
        given: "a loader with multiple URLs"
        def locations = [
            "https://example.com/rules/rule1.drl",
            "https://example.com/rules/rule2.drl",
            "https://example.com/rules/rule3.drl"
        ]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "loader is created successfully"
        loader.locations.size() == 3
    }

    def "should skip loading when loader is disabled"() {
        given: "a disabled loader"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, false, 100, [:])

        when: "loadRules is called"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown and nothing is loaded"
        noExceptionThrown()
    }

    def "should use convenience constructor with default priority"() {
        given: "a loader created with convenience constructor"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, [:])

        expect: "priority defaults to 100"
        loader.priority == 100
    }

    def "should use convenience constructor with empty properties"() {
        given: "a loader created with convenience constructor"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true)

        expect: "loader is created successfully"
        loader.loaderType == "url"
    }

    // ========== URL Validation Tests ==========

    def "should validate URL format during configuration validation"() {
        given: "a loader with valid URL"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should reject URL without scheme"() {
        given: "a loader with URL missing scheme"
        def locations = ["example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("HTTP") || ex.message.contains("scheme")
    }

    def "should reject URL with unsupported scheme"() {
        given: "a loader with unsupported scheme"
        def locations = [url]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message != null

        where:
        url << [
            "ftp://example.com/rules/test.drl",
            "file:///etc/passwd",
            "javascript:alert('xss')"
        ]
    }

    def "should reject null URL"() {
        given: "a loader with null in locations"
        def locations = [null]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should reject empty URL"() {
        given: "a loader with empty URL"
        def locations = [""]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should reject URL without host"() {
        given: "a loader with URL missing host"
        def locations = ["https:///rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("host")
    }

    // ========== SSRF Protection Tests ==========

    def "should block localhost by default"() {
        given: "a loader with localhost URL"
        def locations = [url]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("not allowed")

        where:
        url << [
            "http://localhost/rules/test.drl",
            "https://localhost:8080/rules/test.drl"
        ]
    }

    def "should block loopback IP by default"() {
        given: "a loader with loopback IP"
        def locations = ["http://127.0.0.1/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("not allowed")
    }

    def "should block private IP ranges by default"() {
        given: "a loader with private IP"
        def locations = [url]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("not allowed")

        where:
        url << [
            "http://192.168.1.1/rules/test.drl",
            "http://10.0.0.1/rules/test.drl",
            "http://172.16.0.1/rules/test.drl",
            "http://172.31.255.255/rules/test.drl"
        ]
    }

    def "should allow localhost when explicitly enabled"() {
        given: "a loader with localhost URL and allow-private-hosts enabled"
        def locations = ["http://localhost/rules/test.drl"]
        def properties = ["allow-private-hosts": "true"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, properties)

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should allow private IPs when explicitly enabled"() {
        given: "a loader with private IP and allow-private-hosts enabled"
        def locations = ["http://192.168.1.1/rules/test.drl"]
        def properties = ["allow-private-hosts": "true"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, properties)

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    // ========== Configuration Properties Tests ==========

    def "should accept custom timeout configuration"() {
        given: "a loader with custom timeout properties"
        def locations = ["https://example.com/rules/test.drl"]
        def properties = [
            "connect-timeout-ms": "5000",
            "read-timeout-ms": "15000"
        ]

        when: "loader is created"
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, properties)

        then: "loader is created successfully"
        loader != null
        loader.loaderType == "url"
    }

    def "should accept authorization header"() {
        given: "a loader with authorization header"
        def locations = ["https://example.com/rules/test.drl"]
        def properties = ["authorization": "Bearer token123"]

        when: "loader is created"
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, properties)

        then: "loader is created successfully"
        loader != null
        loader.loaderType == "url"
    }

    def "should accept custom headers"() {
        given: "a loader with custom headers"
        def locations = ["https://example.com/rules/test.drl"]
        def properties = [
            "header.X-API-Key": "secret-key",
            "header.X-Custom": "custom-value"
        ]

        when: "loader is created"
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, properties)

        then: "loader is created successfully"
        loader != null
        loader.loaderType == "url"
    }

    def "should handle invalid timeout values gracefully"() {
        given: "a loader with invalid timeout values"
        def locations = ["https://example.com/rules/test.drl"]
        def properties = [
            "connect-timeout-ms": "invalid",
            "read-timeout-ms": "not-a-number"
        ]

        when: "loader is created"
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, properties)

        then: "loader is created (defaults are used for invalid values)"
        loader != null
        loader.loaderType == "url"
    }

    // ========== General Configuration Tests ==========

    def "should reject null locations"() {
        when: "creating loader with null locations"
        new UrlRuleLoader(null, true, 100, [:])

        then: "exception is thrown"
        thrown(NullPointerException)
    }

    def "should reject empty locations list"() {
        given: "a loader with empty locations"
        @Subject
        def loader = new UrlRuleLoader([], true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("No locations configured")
    }

    def "should return correct loader type"() {
        given: "a URL loader"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "loader type is 'url'"
        loader.loaderType == "url"
    }

    def "should return enabled status correctly"() {
        given: "loaders with different enabled states"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def enabledLoader = new UrlRuleLoader(locations, true, 100, [:])
        @Subject
        def disabledLoader = new UrlRuleLoader(locations, false, 100, [:])

        expect: "enabled status matches configuration"
        enabledLoader.enabled == true
        disabledLoader.enabled == false
    }

    def "should return correct priority"() {
        given: "loaders with different priorities"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def highPriorityLoader = new UrlRuleLoader(locations, true, 10, [:])
        @Subject
        def lowPriorityLoader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "priority matches configuration"
        highPriorityLoader.priority == 10
        lowPriorityLoader.priority == 100
    }

    def "should return immutable copy of locations"() {
        given: "a loader with locations"
        def locations = ["https://example.com/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "getting locations"
        def returnedLocations = loader.locations

        then: "returned list is not the same instance"
        !returnedLocations.is(locations)

        and: "attempting to modify returned list throws exception"
        when:
        returnedLocations.add("new-location")

        then:
        thrown(UnsupportedOperationException)
    }

    // ========== Error Handling Tests ==========

    def "should handle malformed URLs during validation"() {
        given: "a loader with malformed URL"
        def locations = ["not-a-valid-url-at-all"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should validate all URLs in configuration"() {
        given: "a loader with mix of valid and invalid URLs"
        def locations = [
            "https://example.com/valid.drl",
            "ftp://invalid.com/invalid.drl"
        ]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown for invalid URL"
        thrown(RuleLoadingException)
    }

    // ========== Edge Cases ==========

    def "should handle URL with port number"() {
        given: "a loader with URL containing port"
        def locations = ["https://example.com:8443/rules/test.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "configuration is valid"
        loader.validateConfiguration()
    }

    def "should handle URL with query parameters"() {
        given: "a loader with URL containing query params"
        def locations = ["https://example.com/rules/test.drl?version=1&format=text"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "configuration is valid"
        loader.validateConfiguration()
    }

    def "should handle URL with fragment"() {
        given: "a loader with URL containing fragment"
        def locations = ["https://example.com/rules/test.drl#section1"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "configuration is valid"
        loader.validateConfiguration()
    }

    def "should handle URL with special characters in path"() {
        given: "a loader with URL containing special characters"
        def locations = ["https://example.com/rules/test-rule_v2.0.drl"]
        @Subject
        def loader = new UrlRuleLoader(locations, true, 100, [:])

        expect: "configuration is valid"
        loader.validateConfiguration()
    }
}
