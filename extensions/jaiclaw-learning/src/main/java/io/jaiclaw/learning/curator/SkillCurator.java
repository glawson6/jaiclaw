package io.jaiclaw.learning.curator;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.learning.LearningProperties;
import io.jaiclaw.learning.skill.LearnedSkillSidecar;
import io.jaiclaw.learning.skill.SkillLifecycle;
import io.jaiclaw.learning.skill.SkillWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Ages learned skills that have stopped being used.
 *
 * <p>{@code ACTIVE → STALE → ARCHIVED} on time since last use. Archived skills
 * are marked, not deleted: a skill the agent wrote records what it learned, and
 * a curator that erased that outright would be destroying evidence to save disk.
 *
 * <p>Pinned skills never transition. An operator saying "keep this" outranks any
 * usage heuristic.
 *
 * <p>The {@link Clock} is injected so ageing can be tested without waiting days.
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public class SkillCurator {

    private static final Logger log = LoggerFactory.getLogger(SkillCurator.class);

    private final SkillWriter skills;
    private final LearningProperties properties;
    private final Clock clock;

    public SkillCurator(SkillWriter skills, LearningProperties properties) {
        this(skills, properties, Clock.systemUTC());
    }

    public SkillCurator(SkillWriter skills, LearningProperties properties, Clock clock) {
        this.skills = skills;
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * Runs one curation pass over a tenant's learned skills.
     *
     * @return what changed, for logging and the actuator
     */
    public CuratorReport curate(String tenantId) {
        if (!properties.curatorEnabled()) {
            return new CuratorReport(tenantId, 0, List.of(), List.of());
        }
        List<String> names = skills.listSkills(tenantId);
        List<String> madeStale = new ArrayList<>();
        List<String> archived = new ArrayList<>();
        Instant now = clock.instant();

        for (String name : names) {
            LearnedSkillSidecar sidecar = skills.readSidecar(tenantId, name).orElse(null);
            if (sidecar == null) continue;
            if (sidecar.pinned()) continue;

            Duration idle = Duration.between(sidecar.lastActivity(), now);
            SkillLifecycle current = sidecar.lifecycle();

            if (current == SkillLifecycle.ACTIVE
                    && idle.compareTo(properties.curatorStaleAfter()) >= 0) {
                skills.writeSidecar(tenantId, name, sidecar.withLifecycle(SkillLifecycle.STALE));
                madeStale.add(name);
            } else if (current == SkillLifecycle.STALE
                    && idle.compareTo(properties.curatorArchiveAfter()) >= 0) {
                skills.writeSidecar(tenantId, name, sidecar.withLifecycle(SkillLifecycle.ARCHIVED));
                archived.add(name);
            }
        }

        if (!madeStale.isEmpty() || !archived.isEmpty()) {
            log.info("Curator for tenant {}: {} stale, {} archived", tenantId,
                    madeStale.size(), archived.size());
        }
        return new CuratorReport(tenantId, names.size(), List.copyOf(madeStale), List.copyOf(archived));
    }

    /**
     * @param tenantId  tenant curated
     * @param inspected how many learned skills were examined
     * @param madeStale skills moved ACTIVE → STALE
     * @param archived  skills moved STALE → ARCHIVED
     */
    public record CuratorReport(String tenantId, int inspected,
                                List<String> madeStale, List<String> archived) {
        public boolean changedAnything() {
            return !madeStale.isEmpty() || !archived.isEmpty();
        }
    }
}
