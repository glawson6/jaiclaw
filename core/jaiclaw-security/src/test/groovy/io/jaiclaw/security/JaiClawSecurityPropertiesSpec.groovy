package io.jaiclaw.security

import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import spock.lang.Specification

class JaiClawSecurityPropertiesSpec extends Specification {

    def "default mode is api-key"() {
        when:
        def props = new JaiClawSecurityProperties()

        then:
        props.mode() == "api-key"
        !props.enabled()
    }

    def "enabled=true without explicit mode resolves to jwt"() {
        when:
        def props = new JaiClawSecurityProperties(true, null, null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                JaiClawSecurityProperties.RateLimitProperties.defaults(),
                false, false, JaiClawSecurityProperties.ApiKeyFilterProperties.defaults())

        then:
        props.mode() == "jwt"
    }

    def "enabled=false without explicit mode resolves to api-key"() {
        when:
        def props = new JaiClawSecurityProperties(false, null, null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                JaiClawSecurityProperties.RateLimitProperties.defaults(),
                false, false, JaiClawSecurityProperties.ApiKeyFilterProperties.defaults())

        then:
        props.mode() == "api-key"
    }

    def "explicit mode overrides enabled flag"() {
        when:
        def props = new JaiClawSecurityProperties(true, "none", null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                JaiClawSecurityProperties.RateLimitProperties.defaults(),
                false, false, JaiClawSecurityProperties.ApiKeyFilterProperties.defaults())

        then:
        props.mode() == "none"
    }

    def "explicit mode 'none' is preserved even when enabled=false"() {
        when:
        def props = new JaiClawSecurityProperties(false, "none", null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                JaiClawSecurityProperties.RateLimitProperties.defaults(),
                false, false, JaiClawSecurityProperties.ApiKeyFilterProperties.defaults())

        then:
        props.mode() == "none"
    }

    def "explicit mode 'api-key' is preserved when enabled=true"() {
        when:
        def props = new JaiClawSecurityProperties(true, "api-key", null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                JaiClawSecurityProperties.RateLimitProperties.defaults(),
                false, false, JaiClawSecurityProperties.ApiKeyFilterProperties.defaults())

        then: "explicit api-key is not overridden to jwt despite enabled=true"
        props.mode() == "api-key"
    }

    def "apiKeyFile defaults to ~/.jaiclaw/api-key"() {
        when:
        def props = new JaiClawSecurityProperties()

        then:
        props.apiKeyFile() == System.getProperty("user.home") + "/.jaiclaw/api-key"
    }

    def "explicit apiKeyFile is preserved"() {
        when:
        def props = new JaiClawSecurityProperties(false, "api-key", null, "/custom/path", false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                JaiClawSecurityProperties.RateLimitProperties.defaults(),
                false, false, JaiClawSecurityProperties.ApiKeyFilterProperties.defaults())

        then:
        props.apiKeyFile() == "/custom/path"
    }

    // --- ApiKeyFilterProperties + RateLimitProperties.skipPaths ---

    def "apiKeyFilter defaults to the hard-coded [/api/health, /webhook/**] skip list"() {
        when:
        def props = new JaiClawSecurityProperties()

        then:
        props.apiKeyFilter() != null
        props.apiKeyFilter().skipPaths() == ["/api/health", "/webhook/**"]
    }

    def "apiKeyFilter.skipPaths defaults kick in when the field is null OR empty"() {
        when:
        def fromNull = new JaiClawSecurityProperties.ApiKeyFilterProperties(null)
        def fromEmpty = new JaiClawSecurityProperties.ApiKeyFilterProperties([])

        then:
        fromNull.skipPaths() == ["/api/health", "/webhook/**"]
        fromEmpty.skipPaths() == ["/api/health", "/webhook/**"]
    }

    def "apiKeyFilter.skipPaths round-trips through List.copyOf to make the list immutable"() {
        given:
        def source = new ArrayList<String>(["/exact", "/prefix/**"])
        def props = new JaiClawSecurityProperties.ApiKeyFilterProperties(source)

        when: "mutate the original after construction"
        source.add("/should-not-appear")

        then: "the copy inside the record is unaffected"
        props.skipPaths() == ["/exact", "/prefix/**"]

        when: "attempt to mutate the stored copy"
        props.skipPaths().add("/nope")

        then:
        thrown(UnsupportedOperationException)
    }

    def "rateLimit.skipPaths defaults to an empty list (whitelist gate handles the primary exclusion)"() {
        when:
        def rl = JaiClawSecurityProperties.RateLimitProperties.defaults()

        then:
        rl.skipPaths() == []
    }

    def "rateLimit.skipPaths round-trips through List.copyOf"() {
        given:
        def source = new ArrayList<String>(["/api/health"])
        def rl = new JaiClawSecurityProperties.RateLimitProperties(true, 60, 60, 300, source)

        when:
        source.add("/api/something-else")

        then: "record copy is unaffected by later source mutation"
        rl.skipPaths() == ["/api/health"]
    }

    // The two "backward-compat overload defaults" cases that used to live
    // here were removed when the 4-arg RateLimitProperties + 10-arg outer
    // constructors were made private to fix a Boot 4 record-binder silent-
    // drop bug (see release notes for the fix). The private overloads are
    // no longer callable from external code, so the tests' premise is moot.

    // --- Boot-4 record-binder regression guard ---
    //
    // When JaiClawSecurityProperties had multiple public constructors, Spring
    // Boot 4's Binder heuristically picked a delegating overload instead of
    // the @ConstructorBinding-annotated canonical one — nested-record fields
    // (like apiKeyFilter.skipPaths) silently received the delegate's
    // hardcoded defaults instead of yaml values. Making the overloads
    // private forces the binder to the canonical constructor. This test
    // fails loudly if the delegating overloads ever leak back to public.

    def "yaml override of api-key-filter.skip-paths round-trips through the record binder"() {
        given: "the exact yaml shape from the reproduction issue"
        Map<String, Object> src = [
                "jaiclaw.security.mode"                        : "api-key",
                "jaiclaw.security.api-key-filter.skip-paths[0]": "/api/health",
                "jaiclaw.security.api-key-filter.skip-paths[1]": "/webhook/**",
                "jaiclaw.security.api-key-filter.skip-paths[2]": "/custom-bypass/**"
        ]
        // Wrap in a single-element list — Groovy's vararg dispatch would
        // otherwise resolve to Binder(Iterable<ConfigurationPropertyName>)
        // because MapConfigurationPropertySource is itself Iterable, and
        // that overload doesn't exist → ClassCastException.
        Binder binder = new Binder([new MapConfigurationPropertySource(src)])

        when:
        JaiClawSecurityProperties props = binder
                .bind("jaiclaw.security", Bindable.of(JaiClawSecurityProperties.class))
                .get()

        then: "the yaml value survives — /custom-bypass/** is not silently dropped"
        props.apiKeyFilter().skipPaths() == ["/api/health", "/webhook/**", "/custom-bypass/**"]
    }

    def "yaml override of rate-limit.skip-paths round-trips through the record binder"() {
        given: "sibling of the api-key-filter case; RateLimitProperties has the same overload shape"
        Map<String, Object> src = [
                "jaiclaw.security.mode"                     : "api-key",
                "jaiclaw.security.rate-limit.enabled"       : "true",
                "jaiclaw.security.rate-limit.skip-paths[0]" : "/api/health"
        ]
        // Wrap in a single-element list — Groovy's vararg dispatch would
        // otherwise resolve to Binder(Iterable<ConfigurationPropertyName>)
        // because MapConfigurationPropertySource is itself Iterable, and
        // that overload doesn't exist → ClassCastException.
        Binder binder = new Binder([new MapConfigurationPropertySource(src)])

        when:
        JaiClawSecurityProperties props = binder
                .bind("jaiclaw.security", Bindable.of(JaiClawSecurityProperties.class))
                .get()

        then:
        props.rateLimit().enabled()
        props.rateLimit().skipPaths() == ["/api/health"]
    }
}
