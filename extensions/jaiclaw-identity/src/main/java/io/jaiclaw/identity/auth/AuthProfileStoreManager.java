package io.jaiclaw.identity.auth;

import io.jaiclaw.core.auth.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages auth profile store persistence with file locking, merge semantics,
 * multi-agent inheritance, and legacy migration.
 */
public class AuthProfileStoreManager {

    private static final Logger log = LoggerFactory.getLogger(AuthProfileStoreManager.class);

    private final Path stateDir;
    private final boolean readOnly;

    public AuthProfileStoreManager(Path stateDir) {
        this(stateDir, false);
    }

    public AuthProfileStoreManager(Path stateDir, boolean readOnly) {
        this.stateDir = stateDir;
        this.readOnly = readOnly || "1".equals(System.getenv(AuthProfileConstants.READ_ONLY_ENV));
    }

    /** Returns the base state directory (e.g. ~/.jaiclaw). */
    public Path stateDir() {
        return stateDir;
    }

    // --- Loading ---

    /** Load store for the main (default) agent. */
    public AuthProfileStore loadMainStore() {
        Path agentDir = resolveMainAgentDir();
        return loadFromDir(agentDir);
    }

    /** Load store for a specific agent directory. */
    public AuthProfileStore loadForAgent(Path agentDir) {
        Path storePath = storePath(agentDir);
        if (!Files.exists(storePath)) {
            // First boot: inherit from main agent
            Path mainDir = resolveMainAgentDir();
            if (!mainDir.equals(agentDir) && Files.exists(storePath(mainDir))) {
                log.info("Sub-agent first boot — inheriting credentials from main agent");
                AuthProfileStore mainStore = loadFromDir(mainDir);
                if (!readOnly) {
                    save(agentDir, mainStore);
                }
                return mainStore;
            }
        }
        return loadFromDir(agentDir);
    }

    /** Load merged view: main + sub-agent (sub overrides main). Used at runtime. */
    public AuthProfileStore loadForRuntime(Path agentDir) {
        if (agentDir == null) {
            return loadMainStore();
        }
        Path mainDir = resolveMainAgentDir();
        if (mainDir.equals(agentDir)) {
            return loadFromDir(agentDir);
        }
        AuthProfileStore mainStore = loadFromDir(mainDir);
        AuthProfileStore agentStore = loadForAgent(agentDir);
        return merge(mainStore, agentStore);
    }

    // --- Saving ---

    /** Save store to the agent directory. Strips inline secrets where refs exist. */
    public void save(Path agentDir, AuthProfileStore store) {
        if (readOnly) {
            log.debug("Read-only mode — skipping save to {}", agentDir);
            return;
        }
        Path storePath = storePath(agentDir);
        Path lockPath = lockPath(agentDir);
        AuthProfileFileLock.withLock(lockPath, () -> {
            try {
                Files.createDirectories(storePath.getParent());
                byte[] json = AuthProfileStoreSerializer.serialize(store);
                Files.write(storePath, json);
                log.debug("Saved auth profile store to {}", storePath);
            } catch (IOException e) {
                log.error("Failed to save auth profile store to {}: {}", storePath, e.getMessage());
                throw new RuntimeException("Failed to save auth profile store", e);
            }
        });
    }

    // --- Mutations (load + modify + save under lock) ---

    /** Add or replace a single profile. */
    public void upsertProfile(Path agentDir, String profileId, AuthProfileCredential credential) {
        Path lockPath = lockPath(agentDir);
        AuthProfileFileLock.withLock(lockPath, () -> {
            AuthProfileStore store = loadFromDir(agentDir);
            AuthProfileStore updated = store.withProfile(profileId, credential);
            saveUnlocked(agentDir, updated);
        });
    }

    /** Remove a single profile. */
    public void removeProfile(Path agentDir, String profileId) {
        Path lockPath = lockPath(agentDir);
        AuthProfileFileLock.withLock(lockPath, () -> {
            AuthProfileStore store = loadFromDir(agentDir);
            AuthProfileStore updated = store.withoutProfile(profileId);
            saveUnlocked(agentDir, updated);
        });
    }

    /** Set the rotation order for a provider. */
    public void setProfileOrder(Path agentDir, String provider, List<String> order) {
        Path lockPath = lockPath(agentDir);
        AuthProfileFileLock.withLock(lockPath, () -> {
            AuthProfileStore store = loadFromDir(agentDir);
            AuthProfileStore updated = store.withOrder(provider.toLowerCase(), order);
            saveUnlocked(agentDir, updated);
        });
    }

    /** Mark a profile as last-known-good for a provider. */
    public void markProfileGood(Path agentDir, String provider, String profileId) {
        Path lockPath = lockPath(agentDir);
        AuthProfileFileLock.withLock(lockPath, () -> {
            AuthProfileStore store = loadFromDir(agentDir);
            AuthProfileStore updated = store.withLastGood(provider.toLowerCase(), profileId);
            saveUnlocked(agentDir, updated);
        });
    }

    // --- Merge ---

    /** Merge two stores: override wins on key conflicts (shallow merge per map). */
    public AuthProfileStore merge(AuthProfileStore base, AuthProfileStore override) {
        if (override.profiles().isEmpty() && override.order().isEmpty()
                && override.lastGood().isEmpty() && override.usageStats().isEmpty()) {
            return base;
        }
        return new AuthProfileStore(
                Math.max(base.version(), override.version()),
                mergeMaps(base.profiles(), override.profiles()),
                mergeMaps(base.order(), override.order()),
                mergeMaps(base.lastGood(), override.lastGood()),
                mergeMaps(base.usageStats(), override.usageStats())
        );
    }

    // --- Multi-agent ---

    /**
     * Adopt a newer OAuth credential from the main agent store.
     * Called before attempting a token refresh in sub-agent context.
     *
     * @return the adopted credential, or empty if main doesn't have a fresher one
     */
    public Optional<OAuthCredential> adoptFromMain(Path subAgentDir, String profileId) {
        Path mainDir = resolveMainAgentDir();
        if (mainDir.equals(subAgentDir)) return Optional.empty();

        AuthProfileStore mainStore = loadFromDir(mainDir);
        AuthProfileCredential mainCred = mainStore.profiles().get(profileId);
        if (!(mainCred instanceof OAuthCredential mainOAuth)) return Optional.empty();

        AuthProfileStore subStore = loadFromDir(subAgentDir);
        AuthProfileCredential subCred = subStore.profiles().get(profileId);

        boolean shouldAdopt = false;
        if (subCred instanceof OAuthCredential subOAuth) {
            // Adopt if main's expires is greater
            shouldAdopt = mainOAuth.expires() > subOAuth.expires();
        } else {
            // Sub-agent doesn't have this profile yet — adopt
            shouldAdopt = mainOAuth.expires() > System.currentTimeMillis();
        }

        if (shouldAdopt) {
            log.info("Adopting newer OAuth credential from main agent for profile '{}'", profileId);
            upsertProfile(subAgentDir, profileId, mainOAuth);
            return Optional.of(mainOAuth);
        }
        return Optional.empty();
    }

    /**
     * Sync a credential to all sibling agent directories (best-effort).
     * Called after a successful OAuth login.
     */
    public void syncToSiblings(Path primaryAgentDir, String profileId, AuthProfileCredential credential) {
        Path agentsRoot = stateDir.resolve(AuthProfileConstants.AGENTS_DIR);
        if (!Files.isDirectory(agentsRoot)) return;

        try (DirectoryStream<Path> siblings = Files.newDirectoryStream(agentsRoot, Files::isDirectory)) {
            for (Path sibling : siblings) {
                Path siblingAgentDir = sibling.resolve("agent");
                if (siblingAgentDir.equals(primaryAgentDir)) continue;
                if (!Files.isDirectory(siblingAgentDir)) continue;

                try {
                    upsertProfile(siblingAgentDir, profileId, credential);
                    log.debug("Synced profile '{}' to sibling agent: {}", profileId, sibling.getFileName());
                } catch (Exception e) {
                    log.debug("Failed to sync profile '{}' to {}: {}",
                            profileId, sibling.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("Failed to enumerate sibling agents: {}", e.getMessage());
        }
    }

    // --- Path resolution ---

    public Path resolveMainAgentDir() {
        String envDir = System.getenv(AuthProfileConstants.AGENT_DIR_ENV);
        if (envDir != null && !envDir.isBlank()) {
            return Path.of(envDir);
        }
        return stateDir.resolve(AuthProfileConstants.AGENTS_DIR).resolve("default").resolve("agent");
    }

    public Path storePath(Path agentDir) {
        return agentDir.resolve(AuthProfileConstants.AUTH_PROFILE_FILENAME);
    }

    public Path lockPath(Path agentDir) {
        return agentDir.resolve(AuthProfileConstants.AUTH_PROFILE_FILENAME + ".lock");
    }

    // --- Internal ---

    private AuthProfileStore loadFromDir(Path agentDir) {
        Path storePath = storePath(agentDir);

        // Try primary store
        if (Files.exists(storePath)) {
            try {
                byte[] json = Files.readAllBytes(storePath);
                AuthProfileStore store = AuthProfileStoreSerializer.deserialize(json);
                log.debug("Loaded {} profiles from {}", store.profiles().size(), storePath);
                return store;
            } catch (IOException e) {
                log.warn("Failed to load auth profile store from {}: {}", storePath, e.getMessage());
            }
        }

        // Try legacy store
        Path legacyPath = agentDir.resolve(AuthProfileConstants.LEGACY_AUTH_FILENAME);
        if (Files.exists(legacyPath)) {
            AuthProfileStore migrated = migrateLegacyStore(legacyPath);
            if (migrated != null) {
                if (!readOnly) {
                    save(agentDir, migrated);
                    try {
                        Files.deleteIfExists(legacyPath);
                        log.info("Migrated legacy auth.json to auth-profiles.json and deleted legacy file");
                    } catch (IOException e) {
                        log.debug("Failed to delete legacy auth file: {}", e.getMessage());
                    }
                }
                return migrated;
            }
        }

        return AuthProfileStore.empty();
    }

    private AuthProfileStore migrateLegacyStore(Path legacyPath) {
        try {
            byte[] json = Files.readAllBytes(legacyPath);
            // Legacy format: flat Record<provider, credential> (no "profiles" wrapper)
            AuthProfileStore parsed = AuthProfileStoreSerializer.deserialize(json);
            if (!parsed.profiles().isEmpty()) {
                return parsed;
            }
            // Try parsing as flat map
            tools.jackson.databind.JsonNode root =
                    AuthProfileStoreSerializer.mapper().readTree(json);
            if (root.isObject() && !root.has("version")) {
                Map<String, AuthProfileCredential> profiles = new LinkedHashMap<>();
                for (Map.Entry<String, tools.jackson.databind.JsonNode> entry : root.properties()) {
                    String profileId = entry.getKey() + ":default";
                    try {
                        AuthProfileCredential cred =
                                AuthProfileStoreSerializer.mapper().treeToValue(
                                        entry.getValue(), AuthProfileCredential.class);
                        if (cred != null) {
                            profiles.put(profileId, cred);
                        }
                    } catch (Exception e) {
                        log.debug("Skipping legacy entry '{}': {}", entry.getKey(), e.getMessage());
                    }
                }
                if (!profiles.isEmpty()) {
                    log.info("Migrated {} profiles from legacy auth.json", profiles.size());
                    return new AuthProfileStore(AuthProfileStore.CURRENT_VERSION,
                            Map.copyOf(profiles), Map.of(), Map.of(), Map.of());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read legacy auth store {}: {}", legacyPath, e.getMessage());
        }
        return null;
    }

    /** Save without acquiring lock (caller must already hold it). */
    private void saveUnlocked(Path agentDir, AuthProfileStore store) {
        if (readOnly) return;
        Path storePath = storePath(agentDir);
        try {
            Files.createDirectories(storePath.getParent());
            byte[] json = AuthProfileStoreSerializer.serialize(store);
            Files.write(storePath, json);
        } catch (IOException e) {
            log.error("Failed to save auth profile store: {}", e.getMessage());
            throw new RuntimeException("Failed to save auth profile store", e);
        }
    }

    private static <K, V> Map<K, V> mergeMaps(Map<K, V> base, Map<K, V> override) {
        if (override.isEmpty()) return base;
        if (base.isEmpty()) return override;
        Map<K, V> merged = new LinkedHashMap<>(base);
        merged.putAll(override);
        return Map.copyOf(merged);
    }
}
