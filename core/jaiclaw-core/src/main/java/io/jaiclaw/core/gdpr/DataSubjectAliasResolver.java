package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

import java.util.List;

/**
 * Expands a data-subject identifier into every identifier the same person's
 * data may be stored under.
 *
 * <h2>Why this is needed</h2>
 * A {@code dataSubjectId} is conventionally the {@code peerId} component of a
 * session key — a raw, channel-scoped platform id. So the same human talking on
 * Slack and on Telegram is <strong>two data subjects</strong>, and an Article 17
 * erasure request satisfies only one of them. That is a compliance defect, not
 * a modelling nicety: the data the user asked to have deleted is still there.
 *
 * <p>An implementation backed by verified identity links resolves the request
 * to a canonical subject and returns every linked channel id, so erasure and
 * export fan out across all of them.
 *
 * <p>Deployments without identity linking need no implementation — the default
 * is identity, preserving today's single-id behaviour exactly.
 *
 * <p>1.4.0.
 */
@Stable
@FunctionalInterface
public interface DataSubjectAliasResolver {

    /**
     * Every identifier this subject's data may be stored under, including the
     * one supplied.
     *
     * <p>Implementations must be <strong>inclusive</strong>: returning fewer
     * ids than the data is stored under means an erasure silently leaves data
     * behind. Returning the input alone is always safe.
     *
     * @param tenantId      tenant scope
     * @param dataSubjectId the identifier the request named
     * @return all equivalent identifiers; never empty, always containing the input
     */
    List<String> aliasesOf(String tenantId, String dataSubjectId);

    /** Resolves nothing — preserves pre-1.4.0 single-identifier behaviour. */
    static DataSubjectAliasResolver identity() {
        return (tenantId, dataSubjectId) -> List.of(dataSubjectId);
    }
}
