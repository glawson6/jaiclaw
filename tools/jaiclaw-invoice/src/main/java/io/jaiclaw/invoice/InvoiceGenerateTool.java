package io.jaiclaw.invoice;

import com.taptech.invoice.core.InvoiceData;
import com.taptech.invoice.core.InvoiceGenerator;
import com.taptech.invoice.core.InvoiceResult;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Generate a PDF invoice from YAML content or structured fields.
 */
public class InvoiceGenerateTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "yaml_content": {
                  "type": "string",
                  "description": "Full YAML content for the invoice. If provided, other fields are ignored."
                },
                "company_name": {
                  "type": "string",
                  "description": "Company name (used when yaml_content is not provided)"
                },
                "client_name": {
                  "type": "string",
                  "description": "Client/bill-to name"
                },
                "invoice_number": {
                  "type": "string",
                  "description": "Invoice number"
                },
                "invoice_date": {
                  "type": "string",
                  "description": "Invoice date"
                },
                "invoice_period": {
                  "type": "string",
                  "description": "Invoice period"
                },
                "default_rate": {
                  "type": "number",
                  "description": "Default hourly rate"
                },
                "line_items": {
                  "type": "array",
                  "description": "Array of line items with date, description, hours, and optional rate",
                  "items": {
                    "type": "object",
                    "properties": {
                      "date": { "type": "string" },
                      "description": { "type": "string" },
                      "hours": { "type": "number" },
                      "rate": { "type": "number" }
                    },
                    "required": ["date", "description", "hours"]
                  }
                },
                "payment_terms": {
                  "type": "string",
                  "description": "Payment terms (default: Due upon receipt)"
                },
                "footer_message": {
                  "type": "string",
                  "description": "Footer message (default: Thank you for your business!)"
                }
              }
            }""";

    private final InvoiceGenerator generator;
    private final InvoiceConfig config;

    public InvoiceGenerateTool(InvoiceGenerator generator, InvoiceConfig config) {
        super(new ToolDefinition(
                "invoice_generate",
                "Generate a professional PDF invoice from YAML content or structured fields (company, client, line items with hours and rates). Returns the generated PDF file path.",
                ToolCatalog.SECTION_INVOICE,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ));
        this.generator = generator;
        this.config = config;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        InvoiceData data = InvoiceToolUtils.resolveInvoiceData(parameters, config);
        Path outputDir = Path.of(config.outputDir());
        Files.createDirectories(outputDir);
        InvoiceResult result = generator.generate(data, outputDir);

        return new ToolResult.Success(
                result.summary(),
                Map.of("file_path", result.outputFile().toAbsolutePath().toString(),
                       "file_type", result.type(),
                       "file_name", result.outputFile().getFileName().toString())
        );
    }
}
