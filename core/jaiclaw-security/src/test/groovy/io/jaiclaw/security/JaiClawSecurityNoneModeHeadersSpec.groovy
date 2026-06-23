package io.jaiclaw.security

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.HeaderWriterFilter
import spock.lang.Specification

import java.lang.reflect.Field

/**
 * Verifies the audit fix for {@code mode=none}: previously
 * {@code NoneSecurityConfiguration.permissiveFilterChain} did not call
 * {@code configureSecurityHeaders}, silently dropping HSTS,
 * X-Frame-Options, Referrer-Policy and X-Content-Type-Options. After
 * the fix the {@link HeaderWriterFilter} must be present and registered
 * with the same writers the {@code api-key} and {@code jwt} chains use.
 *
 * <p>The spec drives the auto-config in a real Spring slice rather than
 * MockMvc (which would require {@code spring-security-test} on the
 * classpath) — introspecting the {@link DefaultSecurityFilterChain}'s
 * filter list is sufficient to prove headers are wired. Spring Boot's
 * {@link SecurityAutoConfiguration} is included so {@code HttpSecurity}
 * is available to the filter-chain bean method.
 */
class JaiClawSecurityNoneModeHeadersSpec extends Specification {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration,
                    JaiClawSecurityAutoConfiguration))
            // server.address=127.0.0.1 satisfies the 0.9.2 fail-fast
            // guard in NoneSecurityConfiguration#verifyBindAddress.
            // See RELEASE-PLAN-0.9.2.md §5.
            .withPropertyValues(
                    "jaiclaw.security.mode=none",
                    "server.address=127.0.0.1")

    def "mode=none chain registers HeaderWriterFilter (configureSecurityHeaders was called)"() {
        when:
        runner.run { ctx ->
            assert ctx.startupFailure == null
            SecurityFilterChain chain = ctx.getBean(SecurityFilterChain)
            assert chain instanceof DefaultSecurityFilterChain
            List filters = ((DefaultSecurityFilterChain) chain).getFilters()

            // HeaderWriterFilter is what Spring Security wires when
            // http.headers(...) is configured. Its presence is the proof
            // that configureSecurityHeaders(http) ran on the chain.
            assert filters.any { it instanceof HeaderWriterFilter }
        }

        then:
        noExceptionThrown()
    }

    def "mode=none HeaderWriterFilter has all four configured writers"() {
        when:
        runner.run { ctx ->
            SecurityFilterChain chain = ctx.getBean(SecurityFilterChain)
            HeaderWriterFilter headerFilter = ((DefaultSecurityFilterChain) chain).getFilters()
                    .find { it instanceof HeaderWriterFilter } as HeaderWriterFilter
            assert headerFilter != null

            // Reflect the headerWriters list — it's the canonical evidence
            // of what configureSecurityHeaders configured.
            Field f = HeaderWriterFilter.getDeclaredField("headerWriters")
            f.setAccessible(true)
            List writers = (List) f.get(headerFilter)

            // Spring Security 6 registers one writer per header-config method
            // we called: contentTypeOptions, frameOptions, referrerPolicy,
            // httpStrictTransportSecurity. Four headers ⇒ at least four
            // writers in the list.
            assert writers.size() >= 4

            // Sanity-check the writers by class name (decoupled from
            // Spring's internal package locations).
            Set<String> writerClassNames = writers.collect { it.class.simpleName } as Set
            assert writerClassNames.contains("XContentTypeOptionsHeaderWriter")
            assert writerClassNames.contains("XFrameOptionsHeaderWriter")
            assert writerClassNames.contains("ReferrerPolicyHeaderWriter")
            assert writerClassNames.contains("HstsHeaderWriter")
        }

        then:
        noExceptionThrown()
    }
}
