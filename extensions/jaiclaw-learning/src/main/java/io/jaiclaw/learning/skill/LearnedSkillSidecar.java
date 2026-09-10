package io.jaiclaw.learning.skill;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Metadata JaiClaw keeps alongside a learned skill, in
 * {@code .jaiclaw-learning.json} next to its {@code SKILL.md}.
 *
 * <p>Kept in a sidecar rather than the SKILL.md frontmatter so the skill file
 * stays exactly what a human would write by hand — a reader should not have to
 * distinguish authored content from bookkeeping.
 *
 * @param skillName   the skill this describes
 * @param tenantId    owning tenant
 * @param originSessionKey session that produced it
 * @param proposalId  proposal that created it
 * @param version     bumped on every patch
 * @param lifecycle   ACTIVE / STALE / ARCHIVED
 * @param pinned      when true the curator never transitions it — an operator's
 *                    explicit "keep this" overrides usage-based ageing
 * @param useCount    times included in a prompt
 * @param createdAt   when first written
 * @param lastUsedAt  when last included in a prompt; null if never
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public record LearnedSkillSidecar(
        String skillName,
        String tenantId,
        String originSessionKey,
        String proposalId,
        int version,
        SkillLifecycle lifecycle,
        boolean pinned,
        long useCount,
        Instant createdAt,
        Instant lastUsedAt
) {
    public LearnedSkillSidecar {
        if (lifecycle == null) lifecycle = SkillLifecycle.ACTIVE;
        if (version < 1) version = 1;
        if (createdAt == null) createdAt = Instant.now();
    }

    public static LearnedSkillSidecar forNewSkill(String skillName, String tenantId,
                                                  String originSessionKey, String proposalId) {
        Instant now = Instant.now();
        return new LearnedSkillSidecar(skillName, tenantId, originSessionKey, proposalId,
                1, SkillLifecycle.ACTIVE, false, 0L, now, null);
    }

    public LearnedSkillSidecar withVersionBump(String newProposalId) {
        return new LearnedSkillSidecar(skillName, tenantId, originSessionKey, newProposalId,
                version + 1, lifecycle, pinned, useCount, createdAt, lastUsedAt);
    }

    public LearnedSkillSidecar withLifecycle(SkillLifecycle newLifecycle) {
        return new LearnedSkillSidecar(skillName, tenantId, originSessionKey, proposalId,
                version, newLifecycle, pinned, useCount, createdAt, lastUsedAt);
    }

    public LearnedSkillSidecar withUse(Instant at) {
        return new LearnedSkillSidecar(skillName, tenantId, originSessionKey, proposalId,
                version, lifecycle, pinned, useCount + 1, createdAt, at);
    }

    public LearnedSkillSidecar withPinned(boolean value) {
        return new LearnedSkillSidecar(skillName, tenantId, originSessionKey, proposalId,
                version, lifecycle, value, useCount, createdAt, lastUsedAt);
    }

    /** When the skill was last used, falling back to creation for a never-used skill. */
    public Instant lastActivity() {
        return lastUsedAt != null ? lastUsedAt : createdAt;
    }
}
