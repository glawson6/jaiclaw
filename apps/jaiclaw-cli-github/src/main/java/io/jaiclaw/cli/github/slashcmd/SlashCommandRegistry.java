package io.jaiclaw.cli.github.slashcmd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Discovers every {@link SlashCommand} bean in the Spring context and
 * indexes them by {@link SlashCommand#name()}. Injected wherever a
 * comment body needs to be parsed and dispatched.
 *
 * <p>Fail-fast on duplicate names — mirrors {@code ToolBeanDiscovery}'s
 * collision behaviour. Two handlers claiming the same slash command is
 * a wiring bug, not a soft ambiguity.
 */
@Component
public class SlashCommandRegistry {

    private static final Logger log = LoggerFactory.getLogger(SlashCommandRegistry.class);

    private final Map<String, SlashCommand> byName = new HashMap<>();

    public SlashCommandRegistry(List<SlashCommand> commands) {
        for (SlashCommand cmd : commands) {
            String name = cmd.name();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(
                        "SlashCommand bean " + cmd.getClass().getName()
                                + " has a null or blank name() — cannot register.");
            }
            String normalised = name.toLowerCase();
            SlashCommand existing = byName.get(normalised);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate slash command '/" + normalised + "' across beans: "
                                + existing.getClass().getName()
                                + " AND " + cmd.getClass().getName()
                                + ". Rename one or remove the duplicate bean.");
            }
            byName.put(normalised, cmd);
        }
        log.info("SlashCommandRegistry registered {} command(s): {}",
                byName.size(), byName.keySet());
    }

    /** Look up by name (case-insensitive). */
    public Optional<SlashCommand> resolve(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(name.toLowerCase()));
    }

    /** All registered commands, for /help. */
    public List<SlashCommand> all() {
        return List.copyOf(byName.values());
    }

    /**
     * Parse a raw comment body. Returns null if the body doesn't start
     * with a slash-command. Otherwise returns the resolved handler + the
     * post-command arguments.
     */
    public Parsed parse(String body) {
        if (body == null) return null;
        String trimmed = body.stripLeading();
        if (!trimmed.startsWith("/")) return null;
        int end = 1;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        String cmdToken = trimmed.substring(1, end);
        String remainder = end < trimmed.length() ? trimmed.substring(end).trim() : "";
        SlashCommand handler = byName.get(cmdToken.toLowerCase());
        if (handler == null) return null;
        return new Parsed(handler, remainder);
    }

    /** Parse result — a resolved handler plus post-command args. */
    public record Parsed(SlashCommand handler, String args) {}
}
