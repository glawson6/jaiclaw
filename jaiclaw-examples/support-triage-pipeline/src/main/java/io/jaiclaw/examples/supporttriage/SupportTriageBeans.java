package io.jaiclaw.examples.supporttriage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Stub beans used as PROCESSOR stages in {@link SupportTriagePipelines}.
 *
 * <p>Everything is in-memory and keyed by a tiny seeded dataset so the example
 * is fully functional without external systems. Real deployments would swap
 * these beans for real REST/JDBC calls.
 */
@Configuration
public class SupportTriageBeans {

    /** Holds the customer id chosen via the shell's {@code set-customer} command. */
    public static final AtomicReference<String> CURRENT_CUSTOMER_ID = new AtomicReference<>("CUST-001");

    private static final Map<String, String> CRM_DB = Map.of(
            "CUST-001", "{\"id\":\"CUST-001\",\"name\":\"Acme Corp\",\"tier\":\"Pro\",\"vip\":false,\"openOrders\":2}",
            "CUST-002", "{\"id\":\"CUST-002\",\"name\":\"Globex\",\"tier\":\"Enterprise\",\"vip\":true,\"openOrders\":7}",
            "CUST-003", "{\"id\":\"CUST-003\",\"name\":\"Initech\",\"tier\":\"Starter\",\"vip\":false,\"openOrders\":0}"
    );

    /** Stub CRM lookup — uses whatever customer id the shell has set. */
    @Bean
    public Function<String, String> crmLookup() {
        return ticket -> {
            String customerId = CURRENT_CUSTOMER_ID.get();
            String account = CRM_DB.getOrDefault(customerId,
                    "{\"id\":\"" + customerId + "\",\"name\":\"Unknown\",\"tier\":\"Unknown\",\"vip\":false}");
            return "TICKET: " + ticket + "\nACCOUNT: " + account;
        };
    }

    /**
     * Routes the resolution either through to the customer or up to a human,
     * based on a {@code confidence: <float>} marker that the agent emits and on
     * whether the upstream context says the customer is a VIP.
     */
    @Bean
    public Function<String, String> escalationGate() {
        return body -> {
            String b = body == null ? "" : body;
            boolean vip = b.contains("\"vip\":true");
            float confidence = extractFloat(b, "confidence:");
            if (vip || confidence < 0.7f) {
                return "ESCALATED:\n" + b
                        + "\n(reason: " + (vip ? "VIP account" : "confidence " + confidence + " < 0.7") + ")";
            }
            return "AUTO_RESOLVED:\n" + b;
        };
    }

    /** Adds case notes / disposition tag for the audit trail. */
    @Bean
    public Function<String, String> closeAndLog() {
        return body -> {
            String b = body == null ? "" : body;
            String disposition = b.startsWith("ESCALATED:") ? "ESCALATED_TO_HUMAN" : "AUTO_CLOSED";
            return b + "\nCASE_NOTES: disposition=" + disposition + ", logged_at=" + java.time.Instant.now();
        };
    }

    private static float extractFloat(String body, String marker) {
        int i = body.indexOf(marker);
        if (i < 0) return 1.0f; // no marker → assume high confidence
        int start = i + marker.length();
        StringBuilder sb = new StringBuilder();
        while (start < body.length() && (Character.isDigit(body.charAt(start)) || body.charAt(start) == '.')) {
            sb.append(body.charAt(start));
            start++;
        }
        try {
            return sb.length() == 0 ? 1.0f : Float.parseFloat(sb.toString());
        } catch (NumberFormatException ex) {
            return 1.0f;
        }
    }
}
