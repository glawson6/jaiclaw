package io.jaiclaw.invoice;

/**
 * Configuration for invoice tools.
 */
public record InvoiceConfig(
        String dataDir,
        String outputDir,
        String defaultCompany,
        double defaultRate
) {
    public InvoiceConfig {
        if (dataDir == null) dataDir = "./invoice-data";
        if (outputDir == null) outputDir = "./invoices";
        if (defaultCompany == null) defaultCompany = "TapTech Holdings, Inc";
        if (defaultRate <= 0) defaultRate = 100.00;
    }
}
