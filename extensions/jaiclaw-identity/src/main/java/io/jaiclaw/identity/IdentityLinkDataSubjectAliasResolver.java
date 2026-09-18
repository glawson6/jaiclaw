package io.jaiclaw.identity;

import io.jaiclaw.core.gdpr.DataSubjectAliasResolver;
import io.jaiclaw.core.model.IdentityLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Expands a data-subject identifier across every channel the same person has a
 * <em>verified</em> identity link for.
 *
 * <p>Closes a real compliance gap. Because {@code dataSubjectId} is
 * conventionally a channel-scoped {@code peerId}, an Article 17 erasure for a
 * user who talks on both Slack and Telegram previously deleted only the half
 * that matched the id in the request.
 *
 * <p>The supplied identifier may be either a channel {@code peerId} or a
 * canonical subject; both resolve to the same alias set. The input is always
 * included, so an unlinked subject behaves exactly as before.
 *
 * <p>Only <strong>verified</strong> links are followed. Honouring an asserted
 * link would let anyone able to call {@code link()} cause another user's data to
 * be deleted — a denial-of-service dressed as a privacy request.
 *
 * <p>1.4.0.
 */
public class IdentityLinkDataSubjectAliasResolver implements DataSubjectAliasResolver {

    private static final Logger log =
            LoggerFactory.getLogger(IdentityLinkDataSubjectAliasResolver.class);

    private final IdentityLinkStore linkStore;

    public IdentityLinkDataSubjectAliasResolver(IdentityLinkStore linkStore) {
        this.linkStore = linkStore;
    }

    @Override
    public List<String> aliasesOf(String tenantId, String dataSubjectId) {
        // A null or blank subject identifies nobody; returning a list containing
        // it would only push the problem downstream into the erasure loop.
        if (dataSubjectId == null || dataSubjectId.isBlank()) {
            return List.of();
        }

        // Inclusive by construction: the input is always present, so a failure
        // to resolve degrades to today's behaviour rather than losing coverage.
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(dataSubjectId);

        if (linkStore == null) {
            return List.copyOf(aliases);
        }

        String canonical = canonicalSubjectFor(dataSubjectId);
        if (canonical == null) {
            return List.copyOf(aliases);
        }
        aliases.add(canonical);

        for (IdentityLink link : linkStore.getLinksForUser(canonical)) {
            if (link.isVerified()) {
                aliases.add(link.channelUserId());
            }
        }

        if (aliases.size() > 1) {
            log.debug("Data subject {} expands to {} identifiers across linked channels",
                    dataSubjectId, aliases.size());
        }
        return List.copyOf(aliases);
    }

    /**
     * Treats the input either as a canonical subject already, or as a channel
     * {@code peerId} to be resolved. The channel is not known here — a
     * {@code peerId} arrives without one — so every verified link is scanned.
     */
    private String canonicalSubjectFor(String dataSubjectId) {
        List<IdentityLink> asCanonical = linkStore.getLinksForUser(dataSubjectId);
        if (!asCanonical.isEmpty()) {
            return dataSubjectId;
        }
        for (IdentityLink link : linkStore.listAll()) {
            if (link.isVerified() && dataSubjectId.equals(link.channelUserId())) {
                return link.canonicalUserId();
            }
        }
        return null;
    }
}
