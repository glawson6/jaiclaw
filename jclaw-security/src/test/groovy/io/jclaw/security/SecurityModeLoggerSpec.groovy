package io.jclaw.security

import spock.lang.Specification

class SecurityModeLoggerSpec extends Specification {

    def "logs info for api-key mode without error"() {
        given:
        def properties = new JClawSecurityProperties(false, "api-key", "test-key", "/tmp/key",
                new JClawSecurityProperties.JwtProperties(),
                new JClawSecurityProperties.RoleMappingProperties(),
                new JClawSecurityProperties.RateLimitProperties())
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
        def properties = new JClawSecurityProperties(false, "none", null, "/tmp/key",
                new JClawSecurityProperties.JwtProperties(),
                new JClawSecurityProperties.RoleMappingProperties(),
                new JClawSecurityProperties.RateLimitProperties())
        def logger = new SecurityModeLogger(properties, null)

        when:
        logger.afterSingletonsInstantiated()

        then:
        noExceptionThrown()
    }

    def "logs info for jwt mode without error"() {
        given:
        def properties = new JClawSecurityProperties(true, "jwt", null, "/tmp/key",
                new JClawSecurityProperties.JwtProperties(),
                new JClawSecurityProperties.RoleMappingProperties(),
                new JClawSecurityProperties.RateLimitProperties())
        def logger = new SecurityModeLogger(properties, null)

        when:
        logger.afterSingletonsInstantiated()

        then:
        noExceptionThrown()
    }
}
