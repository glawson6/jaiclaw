package io.jclaw.security

import spock.lang.Specification

class JClawSecurityPropertiesSpec extends Specification {

    def "default mode is api-key"() {
        when:
        def props = new JClawSecurityProperties()

        then:
        props.mode() == "api-key"
        !props.enabled()
    }

    def "enabled=true without explicit mode resolves to jwt"() {
        when:
        def props = new JClawSecurityProperties(true, null, null, null,
                new JClawSecurityProperties.JwtProperties(),
                new JClawSecurityProperties.RoleMappingProperties(),
                new JClawSecurityProperties.RateLimitProperties())

        then:
        props.mode() == "jwt"
    }

    def "enabled=false without explicit mode resolves to api-key"() {
        when:
        def props = new JClawSecurityProperties(false, null, null, null,
                new JClawSecurityProperties.JwtProperties(),
                new JClawSecurityProperties.RoleMappingProperties(),
                new JClawSecurityProperties.RateLimitProperties())

        then:
        props.mode() == "api-key"
    }

    def "explicit mode overrides enabled flag"() {
        when:
        def props = new JClawSecurityProperties(true, "none", null, null,
                new JClawSecurityProperties.JwtProperties(),
                new JClawSecurityProperties.RoleMappingProperties(),
                new JClawSecurityProperties.RateLimitProperties())

        then:
        props.mode() == "none"
    }

    def "apiKeyFile defaults to ~/.jclaw/api-key"() {
        when:
        def props = new JClawSecurityProperties()

        then:
        props.apiKeyFile() == System.getProperty("user.home") + "/.jclaw/api-key"
    }

    def "explicit apiKeyFile is preserved"() {
        when:
        def props = new JClawSecurityProperties(false, "api-key", null, "/custom/path",
                new JClawSecurityProperties.JwtProperties(),
                new JClawSecurityProperties.RoleMappingProperties(),
                new JClawSecurityProperties.RateLimitProperties())

        then:
        props.apiKeyFile() == "/custom/path"
    }
}
