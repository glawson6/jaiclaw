package io.jaiclaw.calendar.config

import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import spock.lang.Specification

/**
 * Boot-4 record-binder regression guard for {@link CalendarProperties}.
 *
 * <p>Before the private-no-arg fix, {@code CalendarProperties} exposed two
 * public constructors (canonical 6-arg + no-arg default). Spring Boot 4's
 * record binder heuristically picked the no-arg one and every yaml/env
 * value for the record was silently dropped — the boot log read
 * {@code Calendar provider initialized: in-memory} regardless of what
 * {@code JAICLAW_CALENDAR_PROVIDER} was set to. See the removed issue
 * file at {@code docs/issues/calendar-env-var-provider-override.md} for
 * the original taptech-platform-app reproduction.
 *
 * <p>Same class of bug as {@link io.jaiclaw.security.JaiClawSecurityProperties}
 * — the CLAUDE.md rule "one public constructor per
 * {@code @ConfigurationProperties} record" prevents it. These specs fail
 * loudly if the no-arg constructor ever leaks back to public.
 */
class CalendarPropertiesBindingSpec extends Specification {

    def "yaml override of jaiclaw.calendar.provider round-trips through the record binder"() {
        given: "the exact repro shape from the taptech-platform-app deployment"
        Map<String, Object> src = [
                "jaiclaw.calendar.enabled" : "true",
                "jaiclaw.calendar.provider": "redis"
        ]
        // Wrap in a single-element list — Groovy's vararg dispatch would
        // otherwise resolve to Binder(Iterable<ConfigurationPropertyName>)
        // because MapConfigurationPropertySource is itself Iterable, and
        // that overload doesn't exist → ClassCastException.
        Binder binder = new Binder([new MapConfigurationPropertySource(src)])

        when:
        CalendarProperties props = binder
                .bind("jaiclaw.calendar", Bindable.of(CalendarProperties.class))
                .get()

        then: "the provider value survives — the in-memory default is not silently substituted"
        props.enabled()
        props.provider() == "redis"
    }

    def "yaml override of nested jaiclaw.calendar.redis.init-tenants round-trips through the record binder"() {
        given: "sibling of the top-level case — RedisConfig has the same overload shape"
        Map<String, Object> src = [
                "jaiclaw.calendar.enabled"                     : "true",
                "jaiclaw.calendar.provider"                    : "redis",
                "jaiclaw.calendar.redis.refresh-on-startup"    : "true",
                "jaiclaw.calendar.redis.init-tenants"          : "tenant-a,tenant-b"
        ]
        Binder binder = new Binder([new MapConfigurationPropertySource(src)])

        when:
        CalendarProperties props = binder
                .bind("jaiclaw.calendar", Bindable.of(CalendarProperties.class))
                .get()

        then: "the nested RedisConfig record honors yaml, not its hardcoded default"
        props.redis() != null
        props.redis().refreshOnStartup()
        props.redis().initTenants() == "tenant-a,tenant-b"
    }
}
