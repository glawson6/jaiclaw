package io.jclaw.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jclaw.core.model.IdentityLink;
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

    public IdentityLinkStore(Path storePath) {
        this.storePath = storePath;
        load();
    }

    public void link(String canonicalUserId, String channel, String channelUserId) {
        IdentityLink link = new IdentityLink(canonicalUserId, channel, channelUserId);
        linksByChannelKey.put(channelKey(channel, channelUserId), link);
        persist();
    }

    public void unlink(String channel, String channelUserId) {
        linksByChannelKey.remove(channelKey(channel, channelUserId));
        persist();
    }

    public Optional<String> resolveCanonicalId(String channel, String channelUserId) {
        IdentityLink link = linksByChannelKey.get(channelKey(channel, channelUserId));
        return link != null ? Optional.of(link.canonicalUserId()) : Optional.empty();
    }

    public List<IdentityLink> getLinksForUser(String canonicalUserId) {
        return linksByChannelKey.values().stream()
                .filter(link -> link.canonicalUserId().equals(canonicalUserId))
                .toList();
    }

    public List<IdentityLink> listAll() {
        return List.copyOf(linksByChannelKey.values());
    }

    public int size() {
        return linksByChannelKey.size();
    }

    private String channelKey(String channel, String channelUserId) {
        return channel + ":" + channelUserId;
    }

    private void load() {
        if (!Files.exists(storePath)) return;
        try {
            IdentityLink[] links = mapper.readValue(storePath.toFile(), IdentityLink[].class);
            for (IdentityLink link : links) {
                linksByChannelKey.put(channelKey(link.channel(), link.channelUserId()), link);
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
