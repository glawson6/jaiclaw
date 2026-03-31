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
                new JaiClawSecurityProperties.RateLimitProperties())

        then:
        props.mode() == "jwt"
    }

    def "enabled=false without explicit mode resolves to api-key"() {
        when:
        def props = new JaiClawSecurityProperties(false, null, null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties())

        then:
        props.mode() == "api-key"
    }

    def "explicit mode overrides enabled flag"() {
        when:
        def props = new JaiClawSecurityProperties(true, "none", null, null, false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties())

        then:
        props.mode() == "none"
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
                new JaiClawSecurityProperties.RateLimitProperties())

        then:
        props.apiKeyFile() == "/custom/path"
    }
}
