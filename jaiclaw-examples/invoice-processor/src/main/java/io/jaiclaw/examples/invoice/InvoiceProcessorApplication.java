package io.jaiclaw.examples.invoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Accounts-payable invoice ingestion pipeline. FILE trigger watches an inbox
 * directory; LLM stages classify and extract; PROCESSOR stages validate against
 * a PO database stub and write the approved record. Parse failures route to a
 * dead-letter queue via {@code errorStrategy: DEAD_LETTER}.
 *
 * <p>See {@link InvoiceProcessorShellCommands} for the {@code inbox <text>}
 * command that drops a synthetic invoice into the watched directory.
 */
@SpringBootApplication
public class InvoiceProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvoiceProcessorApplication.class, args);
    }
}
