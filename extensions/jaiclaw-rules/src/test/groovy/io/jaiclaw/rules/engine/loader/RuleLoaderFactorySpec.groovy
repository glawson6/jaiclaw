package io.jaiclaw.rules.engine.loader

import io.jaiclaw.rules.engine.config.DroolsProperties
import spock.lang.Specification
import spock.lang.Subject

/**
 * Comprehensive Spock tests for RuleLoaderFactory.
 * Tests cover:
 * - Loader creation from configuration
 * - Loader type resolution
 * - Priority sorting
 * - Validation
 * - Default loader creation
 */
class RuleLoaderFactorySpec extends Specification {

    @Subject
    RuleLoaderFactory factory = new RuleLoaderFactory()

    // ========== Loader Creation Tests ==========

    def "should create classpath loader from configuration"() {
        given: "a configuration for classpath loader"
        def config = new DroolsProperties.RuleLoaderConfig(
            type: "classpath",
            locations: ["rules/**/*.drl"],
            enabled: true,
            priority: 10
        )

        when: "loader is created"
        def loader = factory.createLoader(config)

        then: "correct loader type is returned"
        loader instanceof ClasspathRuleLoader
        loader.loaderType == "classpath"
        loader.locations == ["rules/**/*.drl"]
        loader.enabled == true
        loader.priority == 10
    }

    def "should create filesystem loader from configuration"() {
        given: "a configuration for filesystem loader"
        def config = new DroolsProperties.RuleLoaderConfig(
            type: "filesystem",
            locations: ["/opt/rules/**/*.drl"],
            enabled: true,
            priority: 20
        )

        when: "loader is created"
        def loader = factory.createLoader(config)

        then: "correct loader type is returned"
        loader instanceof FileSystemRuleLoader
        loader.loaderType == "filesystem"
        loader.locations == ["/opt/rules/**/*.drl"]
        loader.enabled == true
        loader.priority == 20
    }

    def "should create URL loader from configuration"() {
        given: "a configuration for URL loader"
        def config = new DroolsProperties.RuleLoaderConfig(
            type: "url",
            locations: ["https://example.com/rules/test.drl"],
            enabled: true,
            priority: 30,
            properties: ["connect-timeout-ms": "5000"]
        )

        when: "loader is created"
        def loader = factory.createLoader(config)

        then: "correct loader type is returned"
        loader instanceof UrlRuleLoader
        loader.loaderType == "url"
        loader.locations == ["https://example.com/rules/test.drl"]
        loader.enabled == true
        loader.priority == 30
    }

    def "should support alternative type names"() {
        given: "configurations with alternative type names"
        def fileConfig = new DroolsProperties.RuleLoaderConfig(
            type: "file",
            locations: ["/opt/rules"],
            enabled: true
        )
        def httpConfig = new DroolsProperties.RuleLoaderConfig(
            type: "http",
            locations: ["http://example.com/test.drl"],
            enabled: true
        )
        def httpsConfig = new DroolsProperties.RuleLoaderConfig(
            type: "https",
            locations: ["https://example.com/test.drl"],
            enabled: true
        )

        when: "loaders are created"
        def fileLoader = factory.createLoader(fileConfig)
        def httpLoader = factory.createLoader(httpConfig)
        def httpsLoader = factory.createLoader(httpsConfig)

        then: "correct loader types are returned"
        fileLoader instanceof FileSystemRuleLoader
        httpLoader instanceof UrlRuleLoader
        httpsLoader instanceof UrlRuleLoader
    }

    def "should throw exception for unsupported loader type"() {
        given: "a configuration with unsupported type"
        def config = new DroolsProperties.RuleLoaderConfig(
            type: "unsupported-type",
            locations: ["some-location"],
            enabled: true
        )

        when: "loader is created"
        factory.createLoader(config)

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("Unsupported loader type")
    }

    // ========== Multiple Loaders Tests ==========

    def "should create multiple loaders from properties"() {
        given: "properties with multiple loader configurations"
        def properties = new DroolsProperties()
        properties.ruleLoaders = [
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["rules/**/*.drl"],
                enabled: true,
                priority: 10
            ),
            new DroolsProperties.RuleLoaderConfig(
                type: "filesystem",
                locations: ["/opt/rules"],
                enabled: true,
                priority: 20
            )
        ]

        when: "loaders are created"
        def loaders = factory.createLoaders(properties)

        then: "correct number of loaders is returned"
        loaders.size() == 2
        loaders[0] instanceof ClasspathRuleLoader
        loaders[1] instanceof FileSystemRuleLoader
    }

    def "should sort loaders by priority"() {
        given: "properties with loaders in wrong priority order"
        def properties = new DroolsProperties()
        properties.ruleLoaders = [
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["rules1"],
                enabled: true,
                priority: 50
            ),
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["rules2"],
                enabled: true,
                priority: 10
            ),
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["rules3"],
                enabled: true,
                priority: 30
            )
        ]

        when: "loaders are created"
        def loaders = factory.createLoaders(properties)

        then: "loaders are sorted by priority (ascending)"
        loaders.size() == 3
        loaders[0].priority == 10
        loaders[1].priority == 30
        loaders[2].priority == 50
    }

    def "should skip disabled loaders"() {
        given: "properties with mix of enabled and disabled loaders"
        def properties = new DroolsProperties()
        properties.ruleLoaders = [
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["rules1"],
                enabled: true,
                priority: 10
            ),
            new DroolsProperties.RuleLoaderConfig(
                type: "filesystem",
                locations: ["/opt/rules"],
                enabled: false,
                priority: 20
            ),
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["rules2"],
                enabled: true,
                priority: 30
            )
        ]

        when: "loaders are created"
        def loaders = factory.createLoaders(properties)

        then: "only enabled loaders are returned"
        loaders.size() == 2
        loaders.every { it.enabled }
    }

    def "should validate loader configurations during creation"() {
        given: "properties with invalid loader configuration"
        def properties = new DroolsProperties()
        properties.ruleLoaders = [
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["../../../etc/passwd"],
                enabled: true
            )
        ]

        when: "loaders are created"
        factory.createLoaders(properties)

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should continue on validation error when fail-fast is disabled"() {
        given: "properties with invalid loader but fail-fast disabled"
        def properties = new DroolsProperties()
        properties.failFast = false
        properties.ruleLoaders = [
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["rules/**/*.drl"],
                enabled: true
            ),
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["../../../etc/passwd"],
                enabled: true
            )
        ]

        when: "loaders are created"
        def loaders = factory.createLoaders(properties)

        then: "valid loader is returned, invalid is skipped"
        loaders.size() == 1
        loaders[0].locations == ["rules/**/*.drl"]
    }

    def "should throw exception when fail-fast is enabled and validation fails"() {
        given: "properties with invalid loader and fail-fast enabled"
        def properties = new DroolsProperties()
        properties.failFast = true
        properties.ruleLoaders = [
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["../../../etc/passwd"],
                enabled: true
            )
        ]

        when: "loaders are created"
        factory.createLoaders(properties)

        then: "exception is thrown immediately"
        thrown(RuleLoadingException)
    }

    // ========== Default Loader Tests ==========

    def "should create default loader when no configuration provided"() {
        given: "empty properties"
        def properties = new DroolsProperties()
        properties.ruleLoaders = []

        when: "loaders are created"
        def loaders = factory.createLoaders(properties)

        then: "default classpath loader is returned"
        loaders.size() == 1
        loaders[0] instanceof ClasspathRuleLoader
        loaders[0].loaderType == "classpath"
        loaders[0].locations.contains("rules/**/*.drl")
    }

    def "should create default loader when properties is null"() {
        given: "properties with null loader list"
        def properties = new DroolsProperties()
        properties.ruleLoaders = null

        when: "loaders are created"
        def loaders = factory.createLoaders(properties)

        then: "default classpath loader is returned"
        loaders.size() == 1
        loaders[0] instanceof ClasspathRuleLoader
    }

    // ========== Error Handling Tests ==========

    def "should throw exception when all loaders are disabled"() {
        given: "properties with only disabled loaders"
        def properties = new DroolsProperties()
        properties.ruleLoaders = [
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["rules"],
                enabled: false
            )
        ]

        when: "loaders are created"
        factory.createLoaders(properties)

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("No valid rule loaders")
    }

    def "should throw exception when all loaders fail validation"() {
        given: "properties where all loaders are invalid"
        def properties = new DroolsProperties()
        properties.failFast = false
        properties.ruleLoaders = [
            new DroolsProperties.RuleLoaderConfig(
                type: "classpath",
                locations: ["../../../etc/passwd"],
                enabled: true
            ),
            new DroolsProperties.RuleLoaderConfig(
                type: "url",
                locations: ["ftp://invalid.com"],
                enabled: true
            )
        ]

        when: "loaders are created"
        factory.createLoaders(properties)

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("No valid rule loaders")
    }

    // ========== Configuration Properties Tests ==========

    def "should pass properties to URL loader"() {
        given: "configuration with custom properties"
        def config = new DroolsProperties.RuleLoaderConfig(
            type: "url",
            locations: ["https://example.com/test.drl"],
            enabled: true,
            properties: [
                "connect-timeout-ms": "5000",
                "read-timeout-ms": "10000",
                "authorization": "Bearer token123"
            ]
        )

        when: "loader is created"
        def loader = factory.createLoader(config)

        then: "loader is created successfully"
        loader instanceof UrlRuleLoader
    }

    def "should handle empty properties map"() {
        given: "configuration with empty properties"
        def config = new DroolsProperties.RuleLoaderConfig(
            type: "url",
            locations: ["https://example.com/test.drl"],
            enabled: true,
            properties: [:]
        )

        when: "loader is created"
        def loader = factory.createLoader(config)

        then: "loader is created successfully"
        loader instanceof UrlRuleLoader
    }

    def "should handle null properties map"() {
        given: "configuration with null properties"
        def config = new DroolsProperties.RuleLoaderConfig(
            type: "url",
            locations: ["https://example.com/test.drl"],
            enabled: true,
            properties: null
        )

        when: "loader is created"
        def loader = factory.createLoader(config)

        then: "loader is created successfully"
        loader instanceof UrlRuleLoader
    }

    // ========== Case Insensitivity Tests ==========

    def "should handle type names case-insensitively"() {
        given: "configurations with various case types"
        def config = new DroolsProperties.RuleLoaderConfig(
            type: typeValue,
            locations: ["test-location"],
            enabled: true
        )

        when: "loader is created"
        def loader = factory.createLoader(config)

        then: "correct loader type is returned"
        loader.class == expectedClass

        where:
        typeValue    | expectedClass
        "CLASSPATH"  | ClasspathRuleLoader
        "Classpath"  | ClasspathRuleLoader
        "FILESYSTEM" | FileSystemRuleLoader
        "FileSystem" | FileSystemRuleLoader
        "URL"        | UrlRuleLoader
        "Url"        | UrlRuleLoader
    }
}
