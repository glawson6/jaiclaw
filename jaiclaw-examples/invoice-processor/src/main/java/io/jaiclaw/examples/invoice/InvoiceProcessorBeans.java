package io.jaiclaw.examples.invoice;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

/**
 * Stub PROCESSOR beans for the invoice-processor pipeline.
 *
 * <p>Holds a tiny in-memory map of valid PO numbers and provides two
 * {@code Function<String,String>} beans:
 * <ul>
 *   <li>{@code invoiceValidator} — checks the {@code po_number:} marker against the map</li>
 *   <li>{@code invoiceNotifier} — appends the approved invoice to a JSONL file
 *       under {@code ~/.jaiclaw/invoice-processor/approved.jsonl} and produces
 *       a one-line summary</li>
 * </ul>
 */
@Configuration
public class InvoiceProcessorBeans {

    /** Home directory for the example's persistent state. */
    public static final Path HOME = Path.of(System.getProperty("user.home"), ".jaiclaw", "invoice-processor");
    /** Watched inbox; the FILE trigger reads from here. */
    public static final Path INBOX = HOME.resolve("inbox");
    /** Appended-to file for approved invoices. */
    public static final Path APPROVED = HOME.resolve("approved.jsonl");

    /** Pre-seeded PO database — vendor name keyed by PO number. */
    private static final Map<String, String> PO_DATABASE = Map.of(
            "PO-1001", "Acme Corp",
            "PO-1002", "Globex Industries",
            "PO-1003", "Initech"
    );

    @PostConstruct
    void initDirs() throws IOException {
        Files.createDirectories(INBOX);
        Files.createDirectories(HOME);
    }

    /**
     * Reads the {@code po_number:} line emitted by the upstream extract stage and
     * appends a {@code validation: MATCH|MISMATCH(...)} line.
     */
    @Bean
    public Function<String, String> invoiceValidator() {
        return body -> {
            String b = body == null ? "" : body;
            String po = scrapeMarker(b, "po_number:");
            if (po == null || po.isBlank()) {
                return b + "\nvalidation: MISMATCH(no PO number found)";
            }
            String vendor = PO_DATABASE.get(po.trim());
            if (vendor == null) {
                return b + "\nvalidation: MISMATCH(unknown PO " + po + ")";
            }
            return b + "\nvalidation: MATCH(vendor=" + vendor + ")";
        };
    }

    /**
     * Persists the final approval record as a single JSONL line and produces a
     * short summary the shell's {@code last-result} can show.
     */
    @Bean
    public Function<String, String> invoiceNotifier() {
        return body -> {
            String b = body == null ? "" : body;
            String po = scrapeMarker(b, "po_number:");
            String amount = scrapeMarker(b, "amount:");
            String vendor = scrapeMarker(b, "validation: MATCH(vendor=");
            if (vendor != null && vendor.endsWith(")")) {
                vendor = vendor.substring(0, vendor.length() - 1);
            }
            String json = "{\"approvedAt\":\"" + Instant.now()
                    + "\",\"po\":\"" + nullToDash(po)
                    + "\",\"vendor\":\"" + nullToDash(vendor)
                    + "\",\"amount\":\"" + nullToDash(amount) + "\"}";
            try {
                Files.writeString(APPROVED, json + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                return b + "\nnotify: FAILED(" + e.getMessage() + ")";
            }
            return b + "\nnotify: APPROVED(po=" + nullToDash(po) + ", vendor=" + nullToDash(vendor)
                    + ", amount=" + nullToDash(amount) + ")";
        };
    }

    private static String scrapeMarker(String body, String marker) {
        int i = body.indexOf(marker);
        if (i < 0) return null;
        int start = i + marker.length();
        int end = body.indexOf('\n', start);
        if (end < 0) end = body.length();
        return body.substring(start, end).trim();
    }

    private static String nullToDash(String s) {
        return s == null ? "-" : s;
    }
}
