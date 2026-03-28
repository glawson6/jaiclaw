package io.jaiclaw.cronmanager.model;

import io.jaiclaw.core.model.CronJob;
import io.jaiclaw.core.tool.ToolProfile;

import java.util.List;

/**
 * Extended cron job definition wrapping {@link CronJob} with per-job agent configuration.
 * Lives in cron-manager (not core) because it is manager-specific metadata.
 *
 * @param cronJob      the base cron job definition
 * @param provider     AI provider override (nullable = use app default)
 * @param model        model name override (nullable = use app default)
 * @param systemPrompt system prompt override (nullable = use default)
 * @param toolProfile  tool profile for this job's agent execution
 * @param skills       skill names to enable (empty = use all bundled)
 */
public record CronJobDefinition(
        CronJob cronJob,
        String provider,
        String model,
        String systemPrompt,
        ToolProfile toolProfile,
        List<String> skills
) {
    public CronJobDefinition {
        if (toolProfile == null) toolProfile = ToolProfile.MINIMAL;
        if (skills == null) skills = List.of();
    }

    public CronJobDefinition(CronJob cronJob) {
        this(cronJob, null, null, null, ToolProfile.MINIMAL, List.of());
    }

    public String id() {
        return cronJob.id();
    }

    public CronJobDefinition withCronJob(CronJob updated) {
        return new CronJobDefinition(updated, provider, model, systemPrompt, toolProfile, skills);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private CronJob cronJob;
        private String provider;
        private String model;
        private String systemPrompt;
        private ToolProfile toolProfile;
        private List<String> skills;

        public Builder cronJob(CronJob cronJob) { this.cronJob = cronJob; return this; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder toolProfile(ToolProfile toolProfile) { this.toolProfile = toolProfile; return this; }
        public Builder skills(List<String> skills) { this.skills = skills; return this; }

        public CronJobDefinition build() {
            return new CronJobDefinition(cronJob, provider, model, systemPrompt, toolProfile, skills);
        }
    }
}
