package io.jaiclaw.core.model;

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
        Instant nextRunAt,
        String tenantId
) {
    public CronJob {
        if (id == null) id = "";
        if (agentId == null) agentId = "default";
        if (timezone == null) timezone = "UTC";
    }

    /** Backward-compatible constructor without tenantId. */
    public CronJob(String id, String name, String agentId, String schedule, String timezone,
                   String prompt, String deliveryChannel, String deliveryTarget,
                   boolean enabled, Instant lastRunAt, Instant nextRunAt) {
        this(id, name, agentId, schedule, timezone, prompt,
                deliveryChannel, deliveryTarget, enabled, lastRunAt, nextRunAt, null);
    }

    public CronJob withNextRunAt(Instant next) {
        return new CronJob(id, name, agentId, schedule, timezone, prompt,
                deliveryChannel, deliveryTarget, enabled, lastRunAt, next, tenantId);
    }

    public CronJob withLastRunAt(Instant last) {
        return new CronJob(id, name, agentId, schedule, timezone, prompt,
                deliveryChannel, deliveryTarget, enabled, last, nextRunAt, tenantId);
    }

    public CronJob withEnabled(boolean enabled) {
        return new CronJob(id, name, agentId, schedule, timezone, prompt,
                deliveryChannel, deliveryTarget, enabled, lastRunAt, nextRunAt, tenantId);
    }

    public CronJob withTenantId(String tenantId) {
        return new CronJob(id, name, agentId, schedule, timezone, prompt,
                deliveryChannel, deliveryTarget, enabled, lastRunAt, nextRunAt, tenantId);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String name;
        private String agentId;
        private String schedule;
        private String timezone;
        private String prompt;
        private String deliveryChannel;
        private String deliveryTarget;
        private boolean enabled;
        private Instant lastRunAt;
        private Instant nextRunAt;
        private String tenantId;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder schedule(String schedule) { this.schedule = schedule; return this; }
        public Builder timezone(String timezone) { this.timezone = timezone; return this; }
        public Builder prompt(String prompt) { this.prompt = prompt; return this; }
        public Builder deliveryChannel(String deliveryChannel) { this.deliveryChannel = deliveryChannel; return this; }
        public Builder deliveryTarget(String deliveryTarget) { this.deliveryTarget = deliveryTarget; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder lastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; return this; }
        public Builder nextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }

        public CronJob build() {
            return new CronJob(id, name, agentId, schedule, timezone, prompt,
                    deliveryChannel, deliveryTarget, enabled, lastRunAt, nextRunAt, tenantId);
        }
    }
}
