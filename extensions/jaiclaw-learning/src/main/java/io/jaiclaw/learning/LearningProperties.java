package io.jaiclaw.learning;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Learning-loop configuration, bound from {@code jaiclaw.learning}.
 *
 * <p>{@code mode} is the master switch:
 * <ul>
 *   <li>{@code off} (default) — no beans load at all;</li>
 *   <li>{@code propose} — the reviewer runs and files proposals, an operator applies them;</li>
 *   <li>{@code auto} — memory and new-skill proposals apply immediately.</li>
 * </ul>
 *
 * <p><strong>{@code propose} is the default deliberately.</strong> Adopters
 * embedding JaiClaw in a service must opt in to autonomous writes; a framework
 * that silently rewrote its own skills on first run would be unusable in a
 * tenant deployment. See the plan's decision log.
 *
 * <p>Bound by the constructor binder, so exactly one public constructor.
 *
 * @param mode                 {@code off | propose | auto}
 * @param reviewMinTurns       minimum session turns before a review is worthwhile
 * @param reviewMinInterval    minimum wall-clock gap between reviews of one session
 * @param maxTranscriptChars   transcript is truncated head+tail to this budget
 * @param proposalsDir         base directory for the proposal store and ledger
 * @param skillsDir            base directory learned skills are written to
 * @param autoAllowPatches     in {@code auto} mode, whether SKILL_PATCH also
 *                             auto-applies. Off by default: patching an existing
 *                             skill is destructive in a way creating one is not.
 * @param curatorEnabled       whether the curator ages unused learned skills
 * @param curatorStaleAfter    unused-for duration before ACTIVE → STALE
 * @param curatorArchiveAfter  unused-for duration before STALE → ARCHIVED
 */
@ConfigurationProperties(prefix = "jaiclaw.learning")
public record LearningProperties(
        @DefaultValue("off") String mode,
        @DefaultValue("4") int reviewMinTurns,
        @DefaultValue("5m") Duration reviewMinInterval,
        @DefaultValue("12000") int maxTranscriptChars,
        @DefaultValue("${user.home}/.jaiclaw/learning") String proposalsDir,
        @DefaultValue("${user.home}/.jaiclaw/skills/learned") String skillsDir,
        @DefaultValue("false") boolean autoAllowPatches,
        @DefaultValue("true") boolean curatorEnabled,
        @DefaultValue("30d") Duration curatorStaleAfter,
        @DefaultValue("90d") Duration curatorArchiveAfter
) {

    public LearningProperties {
        if (mode == null || mode.isBlank()) mode = "off";
        mode = mode.trim().toLowerCase(java.util.Locale.ROOT);
        if (reviewMinTurns < 1) reviewMinTurns = 1;
        if (reviewMinInterval == null || reviewMinInterval.isNegative()) reviewMinInterval = Duration.ofMinutes(5);
        if (maxTranscriptChars < 500) maxTranscriptChars = 500;
        if (curatorStaleAfter == null || curatorStaleAfter.isNegative()) curatorStaleAfter = Duration.ofDays(30);
        if (curatorArchiveAfter == null || curatorArchiveAfter.isNegative()) curatorArchiveAfter = Duration.ofDays(90);
    }

    /** Programmatic defaults; never seen by the binder. */
    public static LearningProperties defaults() {
        return new LearningProperties("off", 4, Duration.ofMinutes(5), 12000,
                System.getProperty("user.home") + "/.jaiclaw/learning",
                System.getProperty("user.home") + "/.jaiclaw/skills/learned",
                false, true, Duration.ofDays(30), Duration.ofDays(90));
    }

    public boolean isOff() {
        return "off".equals(mode);
    }

    /** True when proposals apply themselves. */
    public boolean isAuto() {
        return "auto".equals(mode);
    }

    /** True when the reviewer should run at all. */
    public boolean isEnabled() {
        return !isOff();
    }
}
