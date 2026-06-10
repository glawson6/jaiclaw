package io.jaiclaw.examples.compintel;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Function;

/**
 * Stub PROCESSOR beans for the competitive-intel briefing pipeline.
 *
 * <p>The {@code signalCacheBean} reads/writes a single JSON-ish blob to the
 * filesystem so successive runs can diff their inputs. The
 * {@code briefingFormatter} writes the assembled markdown briefing to disk.
 * Both deliberately use plain strings — no schema validation — so the example
 * stays readable.
 */
@Configuration
public class CompetitiveIntelBeans {

    public static final Path HOME = Path.of(System.getProperty("user.home"), ".jaiclaw", "competitive-intel");
    public static final Path CACHE = HOME.resolve("last-run.json");
    public static final Path BRIEFINGS_DIR = HOME.resolve("briefings");

    @PostConstruct
    void initDirs() throws IOException {
        Files.createDirectories(BRIEFINGS_DIR);
    }

    /**
     * Reads the previous run's cached signals, writes the current run's signals
     * to disk, and emits a coarse "what changed" delta string the downstream
     * agent can reason about.
     */
    @Bean
    public Function<String, String> signalCacheBean() {
        return currentSignals -> {
            String prior = "";
            try {
                if (Files.exists(CACHE)) {
                    prior = Files.readString(CACHE);
                }
                Files.writeString(CACHE, currentSignals == null ? "" : currentSignals);
            } catch (IOException e) {
                return "cache: FAILED(" + e.getMessage() + ")\nCURRENT:\n" + currentSignals;
            }
            // Single-line per-character delta marker. Enough signal for the demo.
            boolean changed = prior == null || !prior.equals(currentSignals);
            return "cache: " + (changed ? "CHANGED" : "UNCHANGED")
                    + "\nPRIOR:\n" + prior
                    + "\n--- end prior ---\nCURRENT:\n" + currentSignals;
        };
    }

    /**
     * Persists the final markdown briefing to
     * {@code ~/.jaiclaw/competitive-intel/briefings/<today>.md} and returns a
     * short summary the shell {@code last-result} can show.
     */
    @Bean
    public Function<String, String> briefingFormatter() {
        return body -> {
            Path target = BRIEFINGS_DIR.resolve(LocalDate.now() + ".md");
            try {
                Files.writeString(target, body == null ? "" : body);
            } catch (IOException e) {
                return "briefing: FAILED(" + e.getMessage() + ")";
            }
            return "briefing: WROTE(" + target + ", " + (body == null ? 0 : body.length()) + " chars)";
        };
    }
}
