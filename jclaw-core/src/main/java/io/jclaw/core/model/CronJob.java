package io.jclaw.core.model;

import java.time.Instant;

/**
 * Definition of a scheduled cron job that runs an agent on a schedule.
 */
public record CronJob(
        String id,
        String name,
        String agentId,
        String schedule,
        String timezone,
        String prompt,
        String deliveryChannel,
        String deliveryTarget,
        boolean enabled,
        Instant lastRunAt,
        Instant nextRunAt
) {
    public CronJob {
        if (id == null) id = "";
        if (agentId == null) agentId = "default";
        if (timezone == null) timezone = "UTC";
    }

    public CronJob withNextRunAt(Instant next) {
        return new CronJob(id, name, agentId, schedule, timezone, prompt,
                deliveryChannel, deliveryTarget, enabled, lastRunAt, next);
    }

    public CronJob withLastRunAt(Instant last) {
        return new CronJob(id, name, agentId, schedule, timezone, prompt,
                deliveryChannel, deliveryTarget, enabled, last, nextRunAt);
    }

    public CronJob withEnabled(boolean enabled) {
        return new CronJob(id, name, agentId, schedule, timezone, prompt,
                deliveryChannel, deliveryTarget, enabled, lastRunAt, nextRunAt);
    }
}
