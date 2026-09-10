package io.jaiclaw.learning.ledger;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * One recorded skill mutation.
 *
 * @param entryId    unique id, used to roll back
 * @param tenantId   tenant the skill belongs to
 * @param proposalId proposal that caused it
 * @param skillName  skill affected
 * @param action     {@code create}, {@code patch} or {@code rollback}
 * @param beforeHash blob hash of the body before, or null when creating
 * @param afterHash  blob hash of the body after
 * @param actor      who applied it
 * @param at         when
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public record LedgerEntry(
        String entryId,
        String tenantId,
        String proposalId,
        String skillName,
        String action,
        String beforeHash,
        String afterHash,
        String actor,
        Instant at
) {
    /** True when this entry created a skill that did not exist before. */
    public boolean isCreate() {
        return beforeHash == null;
    }
}
