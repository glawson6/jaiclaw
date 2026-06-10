package io.jaiclaw.examples.salesenrich;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.function.Function;

/**
 * Stub PROCESSOR beans for the sales-enrichment pipeline.
 *
 * <p>{@code loadNewLeads} pops one lead from the repository per pipeline
 * invocation; if the queue is empty it returns {@code NO_LEADS} so the
 * downstream agents can detect-and-skip.
 *
 * <p>{@code crmWriteBack} appends the final enriched record to a JSONL file
 * for offline inspection.
 */
@Configuration
public class SalesEnrichmentBeans {

    public static final Path HOME = Path.of(System.getProperty("user.home"), ".jaiclaw", "sales-enrichment");
    public static final Path ENRICHED_FILE = HOME.resolve("enriched.jsonl");

    private final CrmLeadRepository repository;

    public SalesEnrichmentBeans(CrmLeadRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initDirs() throws IOException {
        Files.createDirectories(HOME);
    }

    @Bean
    public Function<String, String> loadNewLeads() {
        return ignored -> {
            CrmLeadRepository.Lead lead = repository.popNext();
            if (lead == null) {
                return "NO_LEADS";
            }
            return "name: " + lead.name() + "\ncompany: " + lead.company();
        };
    }

    @Bean
    public Function<String, String> crmWriteBack() {
        return body -> {
            String b = body == null ? "" : body;
            if (b.startsWith("NO_LEADS") || b.contains("NO_LEADS")) {
                return "skip: NO_LEADS";
            }
            String oneLine = b.replace("\n", " \\n ");
            String json = "{\"enrichedAt\":\"" + Instant.now()
                    + "\",\"record\":\"" + oneLine.replace("\"", "\\\"") + "\"}";
            try {
                Files.writeString(ENRICHED_FILE, json + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                return "write: FAILED(" + e.getMessage() + ")";
            }
            repository.recordEnriched(json);
            return b + "\nwrite: WROTE(jsonl)";
        };
    }
}
