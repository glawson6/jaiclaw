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
    public CalendarProperties() {
        this(true, "in-memory", "default", "default", 30, new RedisConfig());
    }

    public record RedisConfig(
            boolean refreshOnStartup,
            String initTenants
    ) {
        public RedisConfig() {
            this(false, "default");
        }
    }
}
