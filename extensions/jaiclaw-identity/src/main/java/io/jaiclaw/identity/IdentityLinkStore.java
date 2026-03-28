package io.jaiclaw.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.model.IdentityLink;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    public void link(String canonicalUserId, String channel, String channelUserId) {
        String tenantId = resolveTenantId();
        IdentityLink link = new IdentityLink(canonicalUserId, channel, channelUserId, tenantId);
        linksByChannelKey.put(channelKey(channel, channelUserId, tenantId), link);
        persist();
    }

    public void unlink(String channel, String channelUserId) {
        String tenantId = resolveTenantId();
        linksByChannelKey.remove(channelKey(channel, channelUserId, tenantId));
        persist();
    }

    public Optional<String> resolveCanonicalId(String channel, String channelUserId) {
        String tenantId = resolveTenantId();
        IdentityLink link = linksByChannelKey.get(channelKey(channel, channelUserId, tenantId));
        return link != null ? Optional.of(link.canonicalUserId()) : Optional.empty();
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
        } catch (IOException e) {
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
