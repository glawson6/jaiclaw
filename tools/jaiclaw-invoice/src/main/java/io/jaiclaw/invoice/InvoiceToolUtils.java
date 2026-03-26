package io.jaiclaw.invoice;

import com.taptech.invoice.core.InvoiceData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Shared utilities for building InvoiceData from tool parameters.
 */
final class InvoiceToolUtils {

    private InvoiceToolUtils() {}

    /**
     * Build InvoiceData from tool parameters — either from yaml_content or structured fields.
     */
    @SuppressWarnings("unchecked")
    static InvoiceData resolveInvoiceData(Map<String, Object> params, InvoiceConfig config) {
        Object yamlContent = params.get("yaml_content");
        if (yamlContent != null && !yamlContent.toString().isBlank()) {
            return InvoiceData.fromYamlString(yamlContent.toString());
        }

        // Build from structured fields
        String companyName = getOrDefault(params, "company_name", config.defaultCompany());
        String clientName = getOrDefault(params, "client_name", "Client");
        String invoiceNumber = getOrDefault(params, "invoice_number",
                String.format("%03d", System.currentTimeMillis() % 1000));
        String invoiceDate = getOrDefault(params, "invoice_date",
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        String invoicePeriod = (String) params.get("invoice_period");
        double defaultRate = params.containsKey("default_rate")
                ? ((Number) params.get("default_rate")).doubleValue()
                : config.defaultRate();
        String paymentTerms = getOrDefault(params, "payment_terms", "Due upon receipt");
        String footerMessage = getOrDefault(params, "footer_message", "Thank you for your business!");

        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("number", invoiceNumber);
        invoice.put("date", invoiceDate);
        if (invoicePeriod != null) {
            invoice.put("period", invoicePeriod);
        }

        Map<String, Object> company = Map.of("name", companyName, "default_rate", defaultRate);
        Map<String, Object> client = Map.of("name", clientName);

        List<Map<String, Object>> lineItems = new ArrayList<>();
        Object rawItems = params.get("line_items");
        if (rawItems instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> lineItem = new LinkedHashMap<>((Map<String, Object>) m);
                    // Apply default rate if not specified per line item
                    if (!lineItem.containsKey("rate")) {
                        lineItem.put("rate", defaultRate);
                    }
                    lineItems.add(lineItem);
                }
            }
        }

        return new InvoiceData(invoice, company, client, lineItems, paymentTerms, footerMessage);
    }

    /**
     * Apply field overrides to an existing InvoiceData.
     */
    static InvoiceData applyOverrides(InvoiceData data, Map<String, Object> overrides, InvoiceConfig config) {
        Map<String, Object> invoice = new LinkedHashMap<>(data.invoice());
        Map<String, Object> company = new LinkedHashMap<>(data.company());
        Map<String, Object> client = new LinkedHashMap<>(data.client());
        String paymentTerms = data.paymentTerms();
        String footerMessage = data.footerMessage();

        if (overrides.containsKey("company_name")) {
            company.put("name", overrides.get("company_name"));
        }
        if (overrides.containsKey("client_name")) {
            client.put("name", overrides.get("client_name"));
        }
        if (overrides.containsKey("invoice_number")) {
            invoice.put("number", overrides.get("invoice_number"));
        }
        if (overrides.containsKey("invoice_date")) {
            invoice.put("date", overrides.get("invoice_date"));
        }
        if (overrides.containsKey("invoice_period")) {
            invoice.put("period", overrides.get("invoice_period"));
        }
        if (overrides.containsKey("default_rate")) {
            company.put("default_rate", overrides.get("default_rate"));
        }
        if (overrides.containsKey("payment_terms")) {
            paymentTerms = overrides.get("payment_terms").toString();
        }
        if (overrides.containsKey("footer_message")) {
            footerMessage = overrides.get("footer_message").toString();
        }

        return new InvoiceData(invoice, company, client, data.lineItems(), paymentTerms, footerMessage);
    }

    private static String getOrDefault(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
