package io.jclaw.cronmanager.model;

import io.jclaw.core.model.CronJob;
import io.jclaw.core.tool.ToolProfile;

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
}
