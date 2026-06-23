package io.jaiclaw.core.secrets;

import io.jaiclaw.core.api.Stable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Reads secrets from a {@code .env}-style file (one {@code KEY=value}
 * per line, {@code #} for comments).
 *
 * <p>Format rules:
 * <ul>
 *   <li>Blank lines and lines starting with {@code #} are ignored.</li>
 *   <li>Lines must match {@code KEY=value}. Whitespace around {@code =}
 *       is trimmed.</li>
 *   <li>Values may be wrapped in single or double quotes; quotes are
 *       stripped. Inner quotes are not escaped — keep secrets simple.</li>
 *   <li>{@code export KEY=value} is accepted for compatibility with
 *       shells; the {@code export} prefix is stripped.</li>
 *   <li>Inline comments after a value are NOT stripped. Hash signs in
 *       values are preserved as-is.</li>
 * </ul>
 *
 * <p>The file is read lazily on first {@link #get(String)} and cached.
 * Call {@link #refresh()} to force a re-read (e.g., after rotation).
 * A missing file is not an error — the provider behaves as empty.
 *
 * <p>0.9.2 secrets baseline.
 */
@Stable
public final class FileSecretsProvider implements SecretsProvider {

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_.-]*)\\s*=\\s*(.*)$");

    private final Path path;
    private final AtomicReference<Map<String, String>> cache = new AtomicReference<>();

    public FileSecretsProvider(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(load().get(key));
    }

    @Override
    public Map<String, String> getAll(String prefix) {
        Map<String, String> all = load();
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public void refresh() {
        cache.set(null);
    }

    /** The path this provider reads from. */
    public Path path() {
        return path;
    }

    private Map<String, String> load() {
        Map<String, String> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        Map<String, String> fresh = parse();
        cache.set(fresh);
        return fresh;
    }

    private Map<String, String> parse() {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return Map.of();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read secrets file: " + path, e);
        }
        Map<String, String> result = new HashMap<>();
        for (String raw : lines) {
            String line = raw.stripLeading();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            var matcher = LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                // Skip malformed lines — be lenient. Production callers
                // can configure FAIL on the resolver to escalate.
                continue;
            }
            String key = matcher.group(1);
            String value = matcher.group(2);
            result.put(key, unquote(value));
        }
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
