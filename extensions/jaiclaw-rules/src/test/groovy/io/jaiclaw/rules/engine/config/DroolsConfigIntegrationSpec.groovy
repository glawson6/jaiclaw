package io.jaiclaw.rules.engine.config

import io.jaiclaw.rules.engine.loader.RuleLoaderFactory
import io.jaiclaw.rules.config.TestConfig
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.StatelessKieSession
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

/**
 * Integration test for DroolsConfig with the configurable rule loader system.
 * Tests the complete Spring configuration with actual rule loading.
 */
@ContextConfiguration(classes = [TestConfig.class])
@TestPropertySource(properties = [
    "drools.rule-loaders[0].type=classpath",
    "drools.rule-loaders[0].locations[0]=rules/**/*.drl",
    "drools.rule-loaders[0].enabled=true",
    "drools.rule-loaders[0].priority=10",
    "drools.cache-enabled=true",
    "drools.fail-fast=true"
])
class DroolsConfigIntegrationSpec extends Specification {

    @Autowired
    DroolsProperties droolsProperties

    @Autowired
    RuleLoaderFactory ruleLoaderFactory

    @Autowired
    DroolsConfig droolsConfig

    KieContainer kieContainer
    StatelessKieSession kieSession

    def setup() {
        // Create beans from autowired config
        kieContainer = droolsConfig.kieContainer()
        kieSession = droolsConfig.kieSession(kieContainer)
    }

    def "should load Drools configuration properties"() {
        expect: "properties are loaded correctly"
        droolsProperties != null
        droolsProperties.ruleLoaders.size() >= 1
        droolsProperties.cacheEnabled == true
        droolsProperties.failFast == true
    }

    def "should create RuleLoaderFactory bean"() {
        expect: "factory is created and injected"
        ruleLoaderFactory != null
    }

    def "should create KieContainer bean"() {
        expect: "KieContainer is created and initialized"
        kieContainer != null
    }

    def "should create StatelessKieSession bean"() {
        expect: "StatelessKieSession is created"
        kieSession != null
    }

    def "should load rules from configured classpath location"() {
        expect: "KieContainer contains loaded rules (no build errors)"
        kieContainer != null

        and: "KieSession can be obtained"
        def session = kieContainer.newStatelessKieSession()
        session != null
    }

    def "should have correct loader configuration"() {
        given: "the configured properties"
        def loaderConfig = droolsProperties.ruleLoaders[0]

        expect: "loader configuration matches test properties"
        loaderConfig.type == "classpath"
        loaderConfig.locations.contains("rules/**/*.drl")
        loaderConfig.enabled == true
        loaderConfig.priority == 10
    }

    def "should create loaders from properties"() {
        when: "loaders are created from properties"
        def loaders = ruleLoaderFactory.createLoaders(droolsProperties)

        then: "at least one loader is created"
        loaders.size() >= 1

        and: "loaders are sorted by priority"
        for (int i = 0; i < loaders.size() - 1; i++) {
            assert loaders[i].priority <= loaders[i + 1].priority
        }
    }
}
