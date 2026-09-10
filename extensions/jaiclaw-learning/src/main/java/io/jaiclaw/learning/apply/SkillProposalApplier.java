package io.jaiclaw.learning.apply;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.learning.LearningProperties;
import io.jaiclaw.learning.ledger.LearningLedger;
import io.jaiclaw.learning.ledger.LedgerEntry;
import io.jaiclaw.learning.proposal.MemoryProposal;
import io.jaiclaw.learning.proposal.Proposal;
import io.jaiclaw.learning.proposal.ProposalService;
import io.jaiclaw.learning.proposal.SkillPatchProposal;
import io.jaiclaw.learning.proposal.SkillProposal;
import io.jaiclaw.learning.skill.LearnedSkillSidecar;
import io.jaiclaw.learning.skill.SkillWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The skill workshop: creates learned skills, patches them, and rolls either back.
 *
 * <p>Three rules this class exists to enforce.
 *
 * <p><strong>A patch must match a unique span.</strong> If {@code findText} does
 * not appear exactly once in the current body, the patch is refused. A guessed
 * edit would silently rewrite the wrong part of a skill that then steers every
 * future session — far worse than declining.
 *
 * <p><strong>Rollback fails closed.</strong> If the ledger's blob for the prior
 * content is missing, rollback refuses rather than restoring partial or wrong
 * bytes.
 *
 * <p><strong>Auto mode does not auto-patch.</strong> Creating a skill is
 * additive; editing an existing one is destructive, so patches stay manual
 * unless {@code jaiclaw.learning.auto-allow-patches=true}.
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public class SkillProposalApplier implements ProposalApplier {

    private static final Logger log = LoggerFactory.getLogger(SkillProposalApplier.class);

    private final SkillWriter skills;
    private final LearningLedger ledger;
    private final ProposalService proposals;
    private final LearningProperties properties;
    private final ProposalApplier memoryDelegate;

    public SkillProposalApplier(SkillWriter skills, LearningLedger ledger,
                                ProposalService proposals, LearningProperties properties,
                                ProposalApplier memoryDelegate) {
        this.skills = skills;
        this.ledger = ledger;
        this.proposals = proposals;
        this.properties = properties;
        this.memoryDelegate = memoryDelegate;
    }

    @Override
    public ApplyResult applyIfEligible(Proposal proposal, String actor) {
        if (proposal == null) return ApplyResult.refused("No proposal given.");
        return switch (proposal) {
            case SkillProposal s -> createSkill(s, actor);
            case SkillPatchProposal s -> patchSkill(s, actor);
            case MemoryProposal m -> memoryDelegate != null
                    ? memoryDelegate.applyIfEligible(m, actor)
                    : ApplyResult.refused("No memory applier is configured.");
        };
    }

    private ApplyResult createSkill(SkillProposal proposal, String actor) {
        String tenantId = proposal.tenantId();
        String name = proposal.skillName();

        if (skills.exists(tenantId, name)) {
            // Creating over an existing skill would destroy it without a patch
            // record. Make the reviewer propose a patch instead.
            return ApplyResult.refused("A skill named '" + name
                    + "' already exists. Propose a patch instead of recreating it.");
        }
        try {
            LearnedSkillSidecar sidecar = LearnedSkillSidecar.forNewSkill(
                    name, tenantId, proposal.originSessionKey(), proposal.id());
            skills.write(tenantId, name, proposal.description(), proposal.body(), sidecar);

            String after = skills.readRaw(tenantId, name).orElse(proposal.body());
            ledger.record(tenantId, proposal.id(), name, "create", null, after, actor);

            proposals.markApplied(tenantId, proposal.id(), actor);
            log.info("Created learned skill '{}' for tenant {} (proposal {})", name, tenantId, proposal.id());
            return ApplyResult.ok("Created skill '" + name
                    + "'. It will be loaded in the next session, not this one.");
        } catch (RuntimeException e) {
            log.warn("Failed to create skill {} for tenant {}", name, tenantId, e);
            return ApplyResult.refused("Could not create skill: " + e.getMessage());
        }
    }

    private ApplyResult patchSkill(SkillPatchProposal proposal, String actor) {
        String tenantId = proposal.tenantId();
        String name = proposal.skillName();

        if ("auto".equals(actor) && !properties.autoAllowPatches()) {
            return ApplyResult.refused("Skill patches require an explicit apply "
                    + "(set jaiclaw.learning.auto-allow-patches=true to change this).");
        }

        Optional<String> currentRaw = skills.readRaw(tenantId, name);
        if (currentRaw.isEmpty()) {
            return ApplyResult.refused("No learned skill named '" + name + "' to patch.");
        }
        String raw = currentRaw.get();

        int occurrences = countOccurrences(raw, proposal.findText());
        if (occurrences == 0) {
            return ApplyResult.refused("The text to replace was not found in skill '" + name + "'.");
        }
        if (occurrences > 1) {
            // Ambiguity here would rewrite the wrong part of a skill that then
            // steers every future session.
            return ApplyResult.refused("The text to replace appears " + occurrences
                    + " times in skill '" + name + "'; it must appear exactly once. "
                    + "Propose a longer, unambiguous span.");
        }

        try {
            String patched = raw.replace(proposal.findText(), proposal.replaceText());
            skills.writeRaw(tenantId, name, patched);

            LearnedSkillSidecar sidecar = skills.readSidecar(tenantId, name)
                    .map(s -> s.withVersionBump(proposal.id()))
                    .orElseGet(() -> LearnedSkillSidecar.forNewSkill(
                            name, tenantId, proposal.originSessionKey(), proposal.id()));
            skills.writeSidecar(tenantId, name, sidecar);

            ledger.record(tenantId, proposal.id(), name, "patch", raw, patched, actor);
            proposals.markApplied(tenantId, proposal.id(), actor);

            log.info("Patched learned skill '{}' for tenant {} to v{}", name, tenantId, sidecar.version());
            return ApplyResult.ok("Patched skill '" + name + "' (now v" + sidecar.version() + ").");
        } catch (RuntimeException e) {
            log.warn("Failed to patch skill {} for tenant {}", name, tenantId, e);
            return ApplyResult.refused("Could not patch skill: " + e.getMessage());
        }
    }

    @Override
    public ApplyResult rollback(Proposal proposal, String actor) {
        if (proposal == null) return ApplyResult.refused("No proposal given.");
        if (proposal instanceof MemoryProposal m) {
            return memoryDelegate != null
                    ? memoryDelegate.rollback(m, actor)
                    : ApplyResult.refused("No memory applier is configured.");
        }

        String tenantId = proposal.tenantId();
        String skillName = switch (proposal) {
            case SkillProposal s -> s.skillName();
            case SkillPatchProposal s -> s.skillName();
            default -> null;
        };
        if (skillName == null) return ApplyResult.refused("Nothing to roll back.");

        Optional<LedgerEntry> entry = ledger.entries(tenantId).stream()
                .filter(e -> proposal.id().equals(e.proposalId()))
                .filter(e -> !"rollback".equals(e.action()))
                .reduce((first, second) -> second);   // most recent
        if (entry.isEmpty()) {
            return ApplyResult.refused("No ledger entry found for proposal " + proposal.id() + ".");
        }
        LedgerEntry e = entry.get();

        try {
            if (e.isCreate()) {
                // Rolling back a creation removes the skill entirely.
                skills.delete(tenantId, skillName);
                ledger.record(tenantId, proposal.id(), skillName, "rollback", e.afterHash() == null
                        ? null : ledger.contentFor(tenantId, e.afterHash()).orElse(null), null, actor);
                proposals.markRolledBack(tenantId, proposal.id(), actor);
                return ApplyResult.ok("Removed skill '" + skillName + "'.");
            }

            Optional<String> before = ledger.contentFor(tenantId, e.beforeHash());
            if (before.isEmpty()) {
                // Fail closed — a partial restore is worse than none.
                return ApplyResult.refused("The previous version of '" + skillName
                        + "' is missing from the ledger; refusing to roll back to unknown content.");
            }
            String current = skills.readRaw(tenantId, skillName).orElse(null);
            skills.writeRaw(tenantId, skillName, before.get());
            ledger.record(tenantId, proposal.id(), skillName, "rollback", current, before.get(), actor);
            proposals.markRolledBack(tenantId, proposal.id(), actor);

            log.info("Rolled back skill '{}' for tenant {} (proposal {})", skillName, tenantId, proposal.id());
            return ApplyResult.ok("Restored the previous version of '" + skillName + "'.");
        } catch (RuntimeException ex) {
            log.warn("Failed to roll back skill {} for tenant {}", skillName, tenantId, ex);
            return ApplyResult.refused("Could not roll back: " + ex.getMessage());
        }
    }

    static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return 0;
        int count = 0;
        int idx = haystack.indexOf(needle);
        while (idx >= 0) {
            count++;
            idx = haystack.indexOf(needle, idx + needle.length());
        }
        return count;
    }
}
