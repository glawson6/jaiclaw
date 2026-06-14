package io.jaiclaw.agentmind.soul.personas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Loads persona markdown files from a configured directory and tracks the
 * currently-active persona per session. Activated personas are spliced into
 * the system prompt by {@code SoulPromptInjector} immediately after the
 * agent-scope Soul.
 *
 * <p>Persona files are plain markdown. The filename stem (without
 * {@code .md}) is the persona name; the file contents are the overlay
 * markdown.
 *
 * <p>State is in-process and per-session — operator says "be a pirate
 * for this session" via the {@code personality} agent tool, the manager
 * records that, and the next prompt build picks up the overlay.
 *
 * <p>Plan §8 task 4.3.
 */
public class PersonaOverlayManager {

    private static final Logger log = LoggerFactory.getLogger(PersonaOverlayManager.class);

    private final Path personasDir;
    private final Map<String, String> personaContent = new HashMap<>();
    private final ConcurrentHashMap<String, String> activeBySession = new ConcurrentHashMap<>();

    public PersonaOverlayManager(Path personasDir) {
        this.personasDir = personasDir;
        reload();
    }

    /**
     * Re-scan the personas directory. Existing active-persona session
     * assignments are preserved; lookups for a now-missing persona return
     * empty.
     */
    public synchronized void reload() {
        personaContent.clear();
        if (personasDir == null || !Files.isDirectory(personasDir)) {
            log.debug("PersonaOverlayManager: dir {} does not exist; no personas loaded", personasDir);
            return;
        }
        try (Stream<Path> files = Files.list(personasDir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                    .forEach(this::loadOne);
        } catch (IOException e) {
            log.warn("PersonaOverlayManager: failed to list {}: {}", personasDir, e.getMessage());
        }
    }

    private void loadOne(Path file) {
        String name = stripMdSuffix(file.getFileName().toString());
        try {
            personaContent.put(name, Files.readString(file));
        } catch (IOException e) {
            log.warn("PersonaOverlayManager: failed to read {}: {}", file, e.getMessage());
        }
    }

    /** Lists known persona names (alphabetical). */
    public List<String> available() {
        return personaContent.keySet().stream().sorted().toList();
    }

    /** Returns true if a persona with this name was loaded. */
    public boolean exists(String name) {
        return personaContent.containsKey(name);
    }

    /**
     * Activate a persona for a session. Returns true if activated; false if
     * the persona name is not known.
     */
    public boolean activate(String sessionKey, String personaName) {
        if (sessionKey == null || personaName == null) return false;
        if (!personaContent.containsKey(personaName)) return false;
        activeBySession.put(sessionKey, personaName);
        return true;
    }

    /** Clear any active persona for the session. */
    public void clear(String sessionKey) {
        if (sessionKey != null) activeBySession.remove(sessionKey);
    }

    /**
     * Return the active persona's markdown for a session, or empty when
     * none active.
     */
    public Optional<String> activeMarkdown(String sessionKey) {
        if (sessionKey == null) return Optional.empty();
        String name = activeBySession.get(sessionKey);
        if (name == null) return Optional.empty();
        String content = personaContent.get(name);
        return content == null ? Optional.empty() : Optional.of(content);
    }

    /** Return the active persona name for a session, or empty when none active. */
    public Optional<String> activeName(String sessionKey) {
        if (sessionKey == null) return Optional.empty();
        return Optional.ofNullable(activeBySession.get(sessionKey));
    }

    private static String stripMdSuffix(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
