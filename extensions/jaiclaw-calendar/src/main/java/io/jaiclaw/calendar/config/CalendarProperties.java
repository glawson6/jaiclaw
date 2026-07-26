package io.jaiclaw.calendar.config;

/**
 * Configuration properties for the calendar extension.
 * Bound via {@code @ConfigurationProperties(prefix = "jaiclaw.calendar")} in auto-configuration.
 */
public record CalendarProperties(
        boolean enabled,
        String provider,
        String defaultTenantId,
        String defaultCalendarName,
        int minimumEventDurationMinutes,
        RedisConfig redis
) {
    /**
     * No-arg default. Deliberately {@code private} — see CLAUDE.md "One
     * public constructor per {@code @ConfigurationProperties} record".
     * Spring Boot 4's record binder picks a public constructor by
     * parameter-count heuristic and can silently choose this no-arg over
     * the canonical, dropping every YAML/env value for the record with no
     * warning. Use {@link #defaults()} for programmatic defaults.
     */
    @SuppressWarnings("unused")
    private CalendarProperties() {
        this(true, "in-memory", "default", "default", 30, RedisConfig.defaults());
    }

    /**
     * Programmatic default instance — {@code enabled=true, provider="in-memory",
     * defaultTenantId="default", defaultCalendarName="default",
     * minimumEventDurationMinutes=30, redis=default}. Used by
     * {@link io.jaiclaw.calendar.config.JaiClawCalendarAutoConfiguration}
     * as the fallback when the {@code jaiclaw.calendar} property prefix
     * is entirely absent.
     */
    public static CalendarProperties defaults() {
        return new CalendarProperties(true, "in-memory", "default", "default", 30, RedisConfig.defaults());
    }

    public record RedisConfig(
            boolean refreshOnStartup,
            String initTenants
    ) {
        /**
         * No-arg default. Deliberately {@code private} — same Boot-4
         * record-binder rationale as the outer {@link CalendarProperties}
         * no-arg. Use {@link #defaults()} for programmatic defaults.
         */
        @SuppressWarnings("unused")
        private RedisConfig() {
            this(false, "default");
        }

        /** Programmatic default instance — {@code refreshOnStartup=false, initTenants="default"}. */
        public static RedisConfig defaults() {
            return new RedisConfig(false, "default");
        }
    }
}
