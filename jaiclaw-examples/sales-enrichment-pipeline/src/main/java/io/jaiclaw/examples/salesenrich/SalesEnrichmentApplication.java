package io.jaiclaw.examples.salesenrich;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sales-enrichment pipeline. CRON trigger pops one un-enriched lead off an
 * in-memory queue per tick and runs it through enrichment → score → outreach
 * drafting → CRM write-back. Each shell {@code run-now} processes one lead
 * (one execution); subsequent invocations pick up the next.
 *
 * <p>Driven from the Spring Shell — see {@link SalesEnrichmentShellCommands}.
 */
@SpringBootApplication
public class SalesEnrichmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(SalesEnrichmentApplication.class, args);
    }
}
