package io.jaiclaw.core.ops;

import io.jaiclaw.core.api.Experimental;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

/**
 * A global, file-backed emergency stop ("ESTOP").
 *
 * <p>While engaged, JaiClaw refuses to start <em>new</em> work: the gateway
 * declines inbound messages, cron skips due jobs, kanban column processors stand
 * down and pipeline triggers return 503. Work already in flight is never killed —
 * an in-progress agent turn runs to completion. The stop is fully resumable.
 *
 * <p>The state is a single sentinel file, by default
 * {@code ~/.jaiclaw/ESTOP}. A file, rather than a database flag, is deliberate:
 * it works with no Redis, no JDBC and no running JVM, so an operator can engage
 * it from a shell (or {@code bin/jaiclaw pause}) even when the application is
 * wedged.
 *
 * <p><strong>Fail safe.</strong> Any file at the sentinel path means "engaged",
 * including an empty or corrupt one. Only a readable JSON body yields a
 * populated {@link Status#reason()}; a body we cannot parse still reports
 * engaged, with a null reason. A stop must never be defeated by a bad write.
 *
 * <p>Resolution order for the sentinel's directory:
 * <ol>
 *   <li>the {@code jaiclaw.home} system property,</li>
 *   <li>the {@code JAICLAW_HOME} environment variable,</li>
 *   <li>{@code $HOME/.jaiclaw}.</li>
 * </ol>
 *
 * <p>This class is pure Java — no Spring — so {@code bin/jaiclaw}'s fast path and
 * the core runtime can share it. Every {@link #isEngaged()} call performs one
 * {@link Files#exists} check with no caching: the whole point is that an operator
 * touching the file takes effect on the very next admission check.
 *
 * <p>Phase 1 of the 1.2.0 plan.
 */
@Experimental
public final class EmergencyStop {

    /** Sentinel file name inside the JaiClaw home directory. */
    public static final String SENTINEL_NAME = "ESTOP";

    /** System property naming the JaiClaw home directory. */
    public static final String HOME_PROPERTY = "jaiclaw.home";

    /** Environment variable naming the JaiClaw home directory. */
    public static final String HOME_ENV = "JAICLAW_HOME";

    private final Path sentinel;

    /** Uses the resolved default sentinel path. */
    public EmergencyStop() {
        this(defaultSentinelPath());
    }

    /**
     * Uses an explicit sentinel path. Primarily for tests and for adopters who
     * keep operational state outside {@code $HOME}.
     */
    public EmergencyStop(Path sentinel) {
        this.sentinel = sentinel;
    }

    /** The resolved sentinel path, whether or not it currently exists. */
    public Path sentinelPath() {
        return sentinel;
    }

    /**
     * Resolves {@code <jaiclaw-home>/ESTOP} using the documented precedence.
     * Never throws; falls back to the working directory if no home can be found.
     */
    public static Path defaultSentinelPath() {
        String home = System.getProperty(HOME_PROPERTY);
        if (home == null || home.isBlank()) home = System.getenv(HOME_ENV);
        if (home == null || home.isBlank()) {
            String userHome = System.getProperty("user.home");
            home = (userHome == null || userHome.isBlank())
                    ? ".jaiclaw"
                    : userHome + "/.jaiclaw";
        }
        return Paths.get(home, SENTINEL_NAME);
    }

    /**
     * Whether new work should be refused.
     *
     * <p>One filesystem check, no caching. Any I/O problem is treated as
     * "engaged" only when the file is known to exist; an unreadable parent
     * directory reports not-engaged, matching the "absent means running" default.
     */
    public boolean isEngaged() {
        return Files.exists(sentinel);
    }

    /**
     * Reads the current state, including the reason if one was recorded.
     * Returns a not-engaged status when the sentinel is absent.
     */
    public Status status() {
        if (!Files.exists(sentinel)) {
            return new Status(false, null, null);
        }
        String reason = null;
        Instant engagedAt = null;
        try {
            String body = Files.readString(sentinel, StandardCharsets.UTF_8).trim();
            if (!body.isEmpty()) {
                reason = extractJsonString(body, "reason");
                String at = extractJsonString(body, "engagedAt");
                if (at != null) {
                    try {
                        engagedAt = Instant.parse(at);
                    } catch (RuntimeException ignored) {
                        // Unparseable timestamp — still engaged, just undated.
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Fail safe: unreadable or corrupt sentinel still means engaged.
        }
        if (engagedAt == null) {
            try {
                engagedAt = Files.getLastModifiedTime(sentinel).toInstant();
            } catch (IOException ignored) {
                // Leave null — the stop is engaged either way.
            }
        }
        return new Status(true, reason, engagedAt);
    }

    /**
     * Engages the stop, writing a small JSON body with the reason and timestamp.
     * Idempotent: engaging an already-engaged stop rewrites the body.
     *
     * <p>The write is atomic (temp file + move) so a reader can never observe a
     * half-written sentinel — though a half-written one would still, correctly,
     * read as engaged.
     *
     * @param reason free-text operator reason; may be null
     * @throws IOException if the sentinel could not be created
     */
    public void engage(String reason) throws IOException {
        Path parent = sentinel.getParent();
        if (parent != null) Files.createDirectories(parent);
        String body = "{\"reason\":" + jsonQuote(reason)
                + ",\"engagedAt\":\"" + Instant.now() + "\"}";
        Path tmp = sentinel.resolveSibling(sentinel.getFileName() + ".tmp");
        Files.writeString(tmp, body, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, sentinel, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, sentinel, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Releases the stop. Idempotent — releasing an already-released stop is a
     * no-op rather than an error.
     *
     * @return {@code true} if a sentinel was actually removed
     * @throws IOException if the sentinel exists but could not be deleted
     */
    public boolean release() throws IOException {
        return Files.deleteIfExists(sentinel);
    }

    /**
     * Current emergency-stop state.
     *
     * @param engaged   whether new work is being refused
     * @param reason    operator-supplied reason, or null if none was recorded
     *                  (including when the sentinel body was unreadable)
     * @param engagedAt when the stop was engaged, best-effort
     */
    public record Status(boolean engaged, String reason, Instant engagedAt) {
        /** The reason, if any was recorded. */
        public Optional<String> reasonIfPresent() {
            return Optional.ofNullable(reason);
        }
    }

    // --- Minimal JSON handling -------------------------------------------------
    // jaiclaw-core has no Jackson dependency by design, and the sentinel body is
    // a two-field object this class writes itself. A tiny reader beats adding a
    // dependency to the module every other module builds on.

    private static String jsonQuote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Extracts a string field from a flat JSON object; null if absent or non-string. */
    private static String extractJsonString(String json, String field) {
        String needle = "\"" + field + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return null;
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null; // null or non-string
        StringBuilder sb = new StringBuilder();
        for (i++; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                return sb.toString();
                            }
                        }
                    }
                    default -> sb.append(n);
                }
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
