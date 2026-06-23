package io.jaiclaw.security

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
                new JaiClawSecurityProperties.RateLimitProperties(),
                false)

        then:
        props.mode() == "jwt"
    }

    def "enabled=false without explicit mode resolves to api-key"() {
        when:
        def props = new JaiClawSecurityProperties(false, null, null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties(),
                false)

        then:
        props.mode() == "api-key"
    }

    def "explicit mode overrides enabled flag"() {
        when:
        def props = new JaiClawSecurityProperties(true, "none", null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties(),
                false)

        then:
        props.mode() == "none"
    }

    def "explicit mode 'none' is preserved even when enabled=false"() {
        when:
        def props = new JaiClawSecurityProperties(false, "none", null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties(),
                false)

        then:
        props.mode() == "none"
    }

    def "explicit mode 'api-key' is preserved when enabled=true"() {
        when:
        def props = new JaiClawSecurityProperties(true, "api-key", null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties(),
                false)

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
                new JaiClawSecurityProperties.RateLimitProperties(),
                false)

        then:
        props.apiKeyFile() == "/custom/path"
    }
}
