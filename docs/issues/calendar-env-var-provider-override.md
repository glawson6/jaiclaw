# `jaiclaw.calendar.provider` env-var override is silently dropped by record binding

**Module:** `jaiclaw-calendar`, `jaiclaw-starter-calendar`
**Severity:** functional gap — Redis calendar provider never wires when the
downstream app configures it via env vars only (no application.yml
override), so calendar events silently lose durability across pod restarts.
**Affects:** any downstream app that sets `JAICLAW_CALENDAR_PROVIDER=redis`
(and friends) via container env vars — the canonical Kubernetes /
Helm deployment shape — with no matching `jaiclaw.calendar.provider:
redis` in its baked-in `application.yml`. Discovered while deploying
`taptech-platform-app` on 2026-07-12 against the vps-prod cluster.

## Summary

`JaiClawCalendarAutoConfiguration.calendarProperties(Environment)` binds
the properties record manually:

```java
@Bean
@ConditionalOnMissingBean
public CalendarProperties calendarProperties(Environment environment) {
    return Binder.get(environment)
            .bind("jaiclaw.calendar", CalendarProperties.class)
            .orElse(new CalendarProperties());
}
```

`Binder.bind(prefix, Class)` on a record with a no-arg constructor
appears not to consult Spring's relaxed-binding rules against env-var
property sources for this particular record shape. In the deployment
below, the `JAICLAW_CALENDAR_*` env vars are visible in
`Environment.getProperty("jaiclaw.calendar.provider")` (verified via
`printenv` inside the container), yet the returned `CalendarProperties`
holds `provider = "in-memory"` — the no-arg constructor default —
rather than `"redis"`.

The visible symptom is the boot log:

```
i.j.c.p.InMemoryCalendarProvider - Initializing in-memory calendar with sample events for next 2 months
i.j.c.p.InMemoryCalendarProvider - Initialized 30 sample events in calendar
i.j.c.c.JaiClawCalendarAutoConfiguration - Calendar provider initialized: in-memory
```

That branch (`provider.initialize()` seeding sample data) only fires
when `properties.provider().equals("in-memory")`, so we know the bind
returned the fallback record.

This is the same class of bug already documented in
[`tool-allow-deny-env-fallback.md`](./tool-allow-deny-env-fallback.md) —
Spring's `Binder` against a jaiclaw record does not reliably pick up
env-var overrides, and jaiclaw's auto-configs have to fall back to
reading `env.getProperty(...)` directly.

## Repro

Deployment YAML (Kubernetes) with no `application.yml` overrides on the
downstream side:

```yaml
env:
  - name: JAICLAW_CALENDAR_ENABLED
    value: "true"
  - name: JAICLAW_CALENDAR_PROVIDER
    value: "redis"
  - name: JAICLAW_CALENDAR_INIT_TENANTS
    value: "default"
  - name: JAICLAW_CALENDAR_REDIS_REFRESH_ON_STARTUP
    value: "false"
```

Downstream classpath has `spring-boot-starter-data-redis-reactive` +
a working `ReactiveStringRedisTemplate` bean (verified — other CRM
stores use it against the same Redis instance).

Inside the running container:

```
$ printenv | grep JAICLAW
JAICLAW_CALENDAR_ENABLED=true
JAICLAW_CALENDAR_PROVIDER=redis
JAICLAW_CALENDAR_INIT_TENANTS=default
JAICLAW_CALENDAR_REDIS_REFRESH_ON_STARTUP=false
```

Expected boot log:

```
i.j.c.c.JaiClawCalendarAutoConfiguration - Calendar provider initialized: redis
```

Actual boot log:

```
i.j.c.p.InMemoryCalendarProvider - Initializing in-memory calendar with sample events for next 2 months
i.j.c.c.JaiClawCalendarAutoConfiguration - Calendar provider initialized: in-memory
```

The `redisCalendarProvider` bean is guarded by
`@ConditionalOnProperty(name = "jaiclaw.calendar.provider", havingValue
= "redis")`. That conditional evaluates against the `Environment`
directly (not the bound record), so if it also skips the bean the
diagnosis is even simpler: `env.getProperty("jaiclaw.calendar.provider")`
returned something other than `"redis"` too. Either way the fix must
make the env-var visible to both the record binder and the
`@ConditionalOnProperty` evaluator.

## Fix — options

**(a) Consult the `Environment` directly for `provider` and re-form the
record.** Mirror the pattern already used for `tools.profile` in
`JaiClawAgentAutoConfiguration`:

```java
public CalendarProperties calendarProperties(Environment environment) {
    CalendarProperties bound = Binder.get(environment)
            .bind("jaiclaw.calendar", CalendarProperties.class)
            .orElse(new CalendarProperties());

    String envProvider = environment.getProperty("jaiclaw.calendar.provider");
    if (envProvider != null && !envProvider.equals(bound.provider())) {
        bound = new CalendarProperties(
                bound.enabled(), envProvider,
                bound.defaultTenantId(), bound.defaultCalendarName(),
                bound.minimumEventDurationMinutes(), bound.redis());
        log.info("Calendar provider resolved from Environment (record binding fallback): {}", envProvider);
    }
    return bound;
}
```

Do the same fallback for `defaultTenantId`, `defaultCalendarName`,
`redis.initTenants`, `redis.refreshOnStartup` — all of them can be
overridden from env vars in a real deployment. Keeps the record as the
public API but ensures the record honors every value in the
`Environment`.

**(b) Use `@ConfigurationProperties` + `@EnableConfigurationProperties`
instead of manual Binder.** This is the standard Spring pattern for
record binding and does honor env-var relaxed binding without a
fallback. Adds a small compile-time cost (the properties class needs
`@ConfigurationProperties(prefix = "jaiclaw.calendar")` at the record
level) and requires an `@EnableConfigurationProperties(CalendarProperties.class)`
on the auto-config. Slightly larger diff, but removes the fallback and
also fixes any *other* record fields we later add without needing to
touch the auto-config again.

Option (b) is the durable fix; option (a) is a per-field patch that
scales with every new record field the extension adds. Recommend (b)
unless there's a reason the current auto-config avoids
`@EnableConfigurationProperties` (e.g. it would double-bind and
`@ConditionalOnMissingBean` guards a downstream override — but that
override pattern is compatible with `@EnableConfigurationProperties`
too, so I don't see the trade-off).

## Downstream workaround

Until this ships, downstream apps can either:

1. Include `jaiclaw.calendar.provider: redis` in a baked-in
   `application.yml` (the record binder *does* read yaml). Trades the
   config-from-env clarity for immediate correctness.
2. Programmatically expose a `CalendarProvider` bean of their own so
   `@ConditionalOnMissingBean(CalendarProvider.class)` on jaiclaw's
   in-memory fallback bows out. More invasive.

## References

- `extensions/jaiclaw-calendar/src/main/java/io/jaiclaw/calendar/config/JaiClawCalendarAutoConfiguration.java`
  — lines ~34–43 (the `Binder.bind(...).orElse(new CalendarProperties())` call)
- `extensions/jaiclaw-calendar/src/main/java/io/jaiclaw/calendar/config/CalendarProperties.java`
  — the record + no-arg constructor default (`provider = "in-memory"`)
- [`tool-allow-deny-env-fallback.md`](./tool-allow-deny-env-fallback.md)
  — same class of bug in the agent auto-config, fixed in 0.9.1 by
  adding an env-fallback layer on top of `Binder`.
