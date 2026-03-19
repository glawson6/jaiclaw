package io.jclaw.identity;

import io.jclaw.core.model.IdentityLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing identity links with validation.
 */
public class IdentityLinkService {

    private static final Logger log = LoggerFactory.getLogger(IdentityLinkService.class);

    private final IdentityLinkStore store;

    public IdentityLinkService(IdentityLinkStore store) {
        this.store = store;
    }

    /**
     * Link a channel user to a canonical user. If the channel user is already linked,
     * updates the link. If canonicalUserId is null, generates a new UUID.
     */
    public IdentityLink link(String canonicalUserId, String channel, String channelUserId) {
        if (canonicalUserId == null || canonicalUserId.isBlank()) {
            canonicalUserId = UUID.randomUUID().toString();
        }
        store.link(canonicalUserId, channel, channelUserId);
        log.info("Linked {}:{} → canonical {}", channel, channelUserId, canonicalUserId);
        return new IdentityLink(canonicalUserId, channel, channelUserId);
    }

    public void unlink(String channel, String channelUserId) {
        store.unlink(channel, channelUserId);
        log.info("Unlinked {}:{}", channel, channelUserId);
    }

    public List<IdentityLink> getLinksForUser(String canonicalUserId) {
        return store.getLinksForUser(canonicalUserId);
    }

    public List<IdentityLink> listAll() {
        return store.listAll();
    }
}
