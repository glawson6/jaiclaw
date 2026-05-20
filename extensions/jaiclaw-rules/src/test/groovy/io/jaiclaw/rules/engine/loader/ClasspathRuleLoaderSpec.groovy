package io.jaiclaw.rules.engine.loader

import org.kie.api.KieServices
import org.kie.api.builder.KieFileSystem
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

/**
 * Comprehensive Spock tests for ClasspathRuleLoader.
 * Tests cover:
 * - Happy path scenarios with various patterns
 * - Edge cases (empty directories, invalid patterns)
 * - Error scenarios (missing resources, invalid content)
 * - Security validation
 * - Configuration validation
 */
class ClasspathRuleLoaderSpec extends Specification {

    def kieServices = KieServices.Factory.get()
    def kieFileSystem

    def setup() {
        kieFileSystem = kieServices.newKieFileSystem()
    }

    def "should load single rule file from classpath"() {
        given: "a loader configured for a specific rule file"
        def locations = ["rules/text-analysis-rules.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should load multiple rule files using wildcard pattern"() {
        given: "a loader configured with wildcard pattern"
        def locations = ["rules/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should load rules recursively with double asterisk pattern"() {
        given: "a loader configured with recursive pattern"
        def locations = ["rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should handle multiple location patterns"() {
        given: "a loader configured with multiple patterns"
        def locations = [
            "rules/text-analysis-rules.drl",
            "rules/decision-rules.drl",
            "rules/validation-rules.drl"
        ]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should skip loading when loader is disabled"() {
        given: "a disabled loader"
        def locations = ["rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, false, 100)

        when: "loadRules is called"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown and nothing is loaded"
        noExceptionThrown()
    }

    def "should throw exception when no rules found"() {
        given: "a loader with non-existent pattern"
        def locations = ["non-existent-rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "exception is thrown when no rules can be loaded"
        thrown(RuleLoadingException)
    }

    def "should throw exception when all locations fail"() {
        given: "a loader with only non-existent locations"
        def locations = ["non-existent/**/*.drl", "also-missing/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should validate configuration on creation"() {
        given: "a loader with valid configuration"
        def locations = ["rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should reject configuration with parent directory reference"() {
        given: "a loader with parent directory reference"
        def locations = ["rules/../other-rules/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("parent directory")
    }

    def "should reject null locations"() {
        when: "creating loader with null locations"
        new ClasspathRuleLoader(null, true, 100)

        then: "exception is thrown"
        thrown(NullPointerException)
    }

    def "should reject empty locations list"() {
        given: "a loader with empty locations"
        @Subject
        def loader = new ClasspathRuleLoader([], true, 100)

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("No locations configured")
    }

    def "should return correct loader type"() {
        given: "a classpath loader"
        def locations = ["rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        expect: "loader type is 'classpath'"
        loader.loaderType == "classpath"
    }

    def "should return enabled status correctly"() {
        given: "loaders with different enabled states"
        def locations = ["rules/**/*.drl"]
        @Subject
        def enabledLoader = new ClasspathRuleLoader(locations, true, 100)
        @Subject
        def disabledLoader = new ClasspathRuleLoader(locations, false, 100)

        expect: "enabled status matches configuration"
        enabledLoader.enabled == true
        disabledLoader.enabled == false
    }

    def "should return correct priority"() {
        given: "loaders with different priorities"
        def locations = ["rules/**/*.drl"]
        @Subject
        def highPriorityLoader = new ClasspathRuleLoader(locations, true, 10)
        @Subject
        def lowPriorityLoader = new ClasspathRuleLoader(locations, true, 100)

        expect: "priority matches configuration"
        highPriorityLoader.priority == 10
        lowPriorityLoader.priority == 100
    }

    def "should return immutable copy of locations"() {
        given: "a loader with locations"
        def locations = ["rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

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

    def "should use convenience constructor with default priority"() {
        given: "a loader created with convenience constructor"
        def locations = ["rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true)

        expect: "priority defaults to 100"
        loader.priority == 100
    }

    def "should handle patterns with classpath prefix"() {
        given: "a loader with classpath-prefixed pattern"
        def locations = ["classpath:rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should handle patterns without classpath prefix"() {
        given: "a loader with non-prefixed pattern"
        def locations = ["rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should generate unique KIE resource paths for multiple files"() {
        given: "a loader that will load multiple files"
        def locations = ["rules/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no duplicate path exceptions occur"
        noExceptionThrown()
    }

    def "should handle configuration with priority=#priority and enabled=#enabled"() {
        given: "a loader with specific configuration"
        def locations = ["rules/**/*.drl"]
        @Subject
        def loader = new ClasspathRuleLoader(locations, enabled, priority)

        expect: "configuration matches"
        loader.priority == priority
        loader.enabled == enabled

        where:
        priority | enabled
        0        | true
        1        | true
        50       | true
        100      | true
        999      | true
        100      | false
    }

    def "should reject locations with suspicious patterns"() {
        given: "a loader with suspicious pattern"
        def locations = [pattern]
        @Subject
        def loader = new ClasspathRuleLoader(locations, true, 100)

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        thrown(RuleLoadingException)

        where:
        pattern << [
            "../../../etc/passwd",
            "rules/../../secrets.drl",
            "..\\windows\\system32\\config"
        ]
    }
}
