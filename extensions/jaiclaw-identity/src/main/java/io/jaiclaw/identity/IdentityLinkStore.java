package io.jaiclaw.identity;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.model.IdentityLink;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists identity links as a JSON file. Thread-safe.
 */
public class IdentityLinkStore {

    private static final Logger log = LoggerFactory.getLogger(IdentityLinkStore.class);

    private final Path storePath;
    private final Map<String, IdentityLink> linksByChannelKey = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final TenantGuard tenantGuard;

    public IdentityLinkStore(Path storePath) {
        this(storePath, null);
    }

    public IdentityLinkStore(Path storePath, TenantGuard tenantGuard) {
        this.storePath = storePath;
        this.tenantGuard = tenantGuard;
        load();
    }

    /**
     * Record an <em>asserted</em> link — nothing proves the binding.
     *
     * <p>Fine for cross-channel conversational continuity; not a basis for
     * authorization. Use {@link #link(String, String, String, String, Instant)}
     * when control of the external identity has actually been proven.
     */
    public void link(String canonicalUserId, String channel, String channelUserId) {
        link(canonicalUserId, channel, channelUserId, null, null);
    }

    /**
     * Record a link, optionally carrying proof of verification.
     *
     * @param issuer     identity provider that vouched for the binding, or null
     * @param verifiedAt when control was proven, or null for an asserted link
     */
    public void link(String canonicalUserId, String channel, String channelUserId,
                     String issuer, Instant verifiedAt) {
        String tenantId = resolveTenantId();
        IdentityLink link = new IdentityLink(canonicalUserId, channel, channelUserId,
                tenantId, issuer, verifiedAt);
        linksByChannelKey.put(channelKey(channel, channelUserId, tenantId), link);
        persist();
    }

    public void unlink(String channel, String channelUserId) {
        String tenantId = resolveTenantId();
        linksByChannelKey.remove(channelKey(channel, channelUserId, tenantId));
        persist();
    }

    public Optional<String> resolveCanonicalId(String channel, String channelUserId) {
        return findLink(channel, channelUserId).map(IdentityLink::canonicalUserId);
    }

    /**
     * The whole link, rather than just its canonical id.
     *
     * <p>Callers that need to know whether the binding was <em>verified</em> —
     * as opposed to merely asserted — must use this and check
     * {@link IdentityLink#isVerified()}. {@link #resolveCanonicalId} discards
     * that distinction.
     */
    public Optional<IdentityLink> findLink(String channel, String channelUserId) {
        String tenantId = resolveTenantId();
        IdentityLink link = linksByChannelKey.get(channelKey(channel, channelUserId, tenantId));
        return Optional.ofNullable(link);
    }

    public List<IdentityLink> getLinksForUser(String canonicalUserId) {
        return filteredLinks()
                .filter(link -> link.canonicalUserId().equals(canonicalUserId))
                .toList();
    }

    public List<IdentityLink> listAll() {
        return filteredLinks().toList();
    }

    public int size() {
        return linksByChannelKey.size();
    }

    private String channelKey(String channel, String channelUserId, String tenantId) {
        if (tenantId != null) {
            return tenantId + ":" + channel + ":" + channelUserId;
        }
        return channel + ":" + channelUserId;
    }

    private String resolveTenantId() {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return null;
    }

    private java.util.stream.Stream<IdentityLink> filteredLinks() {
        java.util.stream.Stream<IdentityLink> stream = linksByChannelKey.values().stream();
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            stream = stream.filter(link -> tenantId.equals(link.tenantId()));
        }
        return stream;
    }

    private void load() {
        if (!Files.exists(storePath)) return;
        try {
            IdentityLink[] links = mapper.readValue(storePath.toFile(), IdentityLink[].class);
            for (IdentityLink link : links) {
                linksByChannelKey.put(channelKey(link.channel(), link.channelUserId(), link.tenantId()), link);
            }
            log.info("Loaded {} identity links from {}", linksByChannelKey.size(), storePath);
        } catch (Exception e) {
            log.warn("Failed to load identity links: {}", e.getMessage());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), linksByChannelKey.values());
        } catch (IOException e) {
            log.error("Failed to persist identity links: {}", e.getMessage());
        }
    }
}
