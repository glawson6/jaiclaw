package io.jaiclaw.learning.skill;

import io.jaiclaw.core.api.Experimental;

/**
 * Lifecycle of an agent-authored skill.
 *
 * <p>{@code ACTIVE → STALE → ARCHIVED}, driven by how long the skill has gone
 * unused. Archived skills are moved aside, never deleted — a skill the agent
 * wrote is a record of what it learned, and losing that outright is worse than
 * carrying a directory that is no longer loaded.
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public enum SkillLifecycle {
    /** Loaded into prompts normally. */
    ACTIVE,
    /** Unused for a while; still loaded, but a candidate for archiving. */
    STALE,
    /** No longer loaded. Retained on disk. */
    ARCHIVED;

    /** Whether the skill loader should include a skill in this state. */
    public boolean isLoadable() {
        return this != ARCHIVED;
    }
}
