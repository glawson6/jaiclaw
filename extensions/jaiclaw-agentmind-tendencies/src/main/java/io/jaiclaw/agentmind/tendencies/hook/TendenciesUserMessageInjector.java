package io.jaiclaw.agentmind.tendencies.hook;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jaiclaw.core.agent.TendenciesStoreProvider;
import io.jaiclaw.core.hook.event.MessageReceivedEvent;
import io.jaiclaw.core.model.Tendencies;
import io.jaiclaw.core.model.TendenciesScope;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Modifying-hook plugin that subscribes to {@link MessageReceivedEvent}
 * and rewrites the inbound message content with a
 * {@code <tendencies-context>}…{@code </tendencies-context>} block (and a
 * {@code <tenant-tendencies>}…{@code </tenant-tendencies>} block when the
 * Phase 5 tenant scope is enabled).
 *
 * <p>Plan §8.3 render caching: rendered blocks are cached per
 * {@code (tenantId, userKey)} in a size-bounded Caffeine cache. The cache
 * key is keyed off the Tendencies record's {@code updatedAt} timestamp so
 * a new dialectic pass naturally invalidates the cache entry on the next
 * lookup.
 *
 * <p>User key resolution: falls back to a deterministic SHA-256 prefix of
 * {@code channelId:peerId} when the soul module's
 * {@code AgentMindUserKeyResolver} isn't on the classpath. Same-channel
 * continuity is preserved regardless; cross-channel continuity requires
 * the linked-identity resolver from {@code jaiclaw-identity}.
 *
 * <p>Plan §8 task 3.8.
 */
public class TendenciesUserMessageInjector implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(TendenciesUserMessageInjector.class);

    private static final int PRIORITY = 300; // after Soul (100) and Memory (200)

    private final TendenciesStoreProvider store;
    private final TenantGuard tenantGuard;
    private final Cache<String, CachedBlock> renderCache;

    public TendenciesUserMessageInjector(TendenciesStoreProvider store, TenantGuard tenantGuard) {
        this(store, tenantGuard, defaultCache());
    }

    /** Test-friendly constructor accepting an injected cache instance. */
    public TendenciesUserMessageInjector(TendenciesStoreProvider store, TenantGuard tenantGuard,
                                          Cache<String, CachedBlock> renderCache) {
        this.store = store;
        this.tenantGuard = tenantGuard;
        this.renderCache = renderCache;
    }

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder()
                .id("agentmind-tendencies-user-message-injector")
                .name("AgentMind Tendencies User Message Injector")
                .description("Splices <tendencies-context> + (when Phase 5 is enabled) "
                        + "<tenant-tendencies> blocks into the inbound user message.")
                .version("1.0.0")
                .kind(PluginKind.GENERAL)
                .build();
    }

    @Override
    public void register(PluginApi api) {
        api.on(MessageReceivedEvent.class, this::rewrite, PRIORITY);
    }

    MessageReceivedEvent rewrite(MessageReceivedEvent event) {
        String tenantId = resolveTenantId();
        String userKey = userKeyFor(event.channelId(), event.peerId());
        if (tenantId == null || userKey == null) return null;

        String block = loadAndRender(tenantId, userKey);
        if (block == null || block.isBlank()) return null;

        String rewritten = block + "\n\n" + nullToEmpty(event.content());
        return new MessageReceivedEvent(event.agentId(), event.sessionKey(),
                event.timestamp(), event.channelId(), event.accountId(),
                event.peerId(), rewritten);
    }

    String loadAndRender(String tenantId, String userKey) {
        try {
            Optional<Tendencies> doc = store.findTendencies(tenantId, TendenciesScope.USER, userKey);
            if (doc.isEmpty()) return null;
            Tendencies t = doc.get();
            String cacheKey = tenantId + ":" + userKey;
            CachedBlock cached = renderCache.getIfPresent(cacheKey);
            if (cached != null && cached.matchesVersion(t.version())) {
                return cached.block;
            }
            String rendered = render(t);
            renderCache.put(cacheKey, new CachedBlock(t.version(), rendered));
            return rendered;
        } catch (Exception e) {
            log.warn("Failed to load Tendencies for {}:{} — skipping injection: {}",
                    tenantId, userKey, e.getMessage());
            return null;
        }
    }

    static String render(Tendencies t) {
        if (t.peerCardMarkdown() == null || t.peerCardMarkdown().isBlank()) return null;
        return "<tendencies-context>\n" + t.peerCardMarkdown().stripTrailing() + "\n</tendencies-context>";
    }

    private String resolveTenantId() {
        if (tenantGuard == null) return "default";
        if (!tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }

    /**
     * Resolve the user key from channelId + peerId. When the soul module's
     * canonical resolver isn't on the classpath (compile-time optional
     * dep), falls back to a deterministic 16-char hex prefix of
     * {@code sha256(channelId:peerId)}.
     */
    static String userKeyFor(String channelId, String peerId) {
        if (peerId == null || peerId.isBlank()) return null;
        if (channelId == null || channelId.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((channelId + ":" + peerId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static Cache<String, CachedBlock> defaultCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }

    /** Cached rendered block keyed on the source Tendencies version. */
    public record CachedBlock(long version, String block) {
        public boolean matchesVersion(long current) {
            return current == this.version;
        }
    }
}
