package io.jaiclaw.security

import spock.lang.Specification

class SecurityModeLoggerSpec extends Specification {

    def "logs info for api-key mode without error"() {
        given:
        def properties = new JaiClawSecurityProperties(false, "api-key", "test-key", "/tmp/key",
                false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties())
        def provider = Stub(ApiKeyProvider) {
            getMaskedKey() >> "****test-key"
            getSource() >> "property"
        }
        def logger = new SecurityModeLogger(properties, provider)

        when:
        logger.afterSingletonsInstantiated()

        then:
        noExceptionThrown()
    }

    def "logs warning for none mode without error"() {
        given:
        def properties = new JaiClawSecurityProperties(false, "none", null, "/tmp/key",
                false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties())
        def logger = new SecurityModeLogger(properties, null)

        when:
        logger.afterSingletonsInstantiated()

        then:
        noExceptionThrown()
    }

    def "logs info for jwt mode without error"() {
        given:
        def properties = new JaiClawSecurityProperties(true, "jwt", null, "/tmp/key",
                false,
                new JaiClawSecurityProperties.JwtProperties(),
                new JaiClawSecurityProperties.RoleMappingProperties(),
                new JaiClawSecurityProperties.RateLimitProperties())
        def logger = new SecurityModeLogger(properties, null)

        when:
        logger.afterSingletonsInstantiated()

        then:
        noExceptionThrown()
    }
}
