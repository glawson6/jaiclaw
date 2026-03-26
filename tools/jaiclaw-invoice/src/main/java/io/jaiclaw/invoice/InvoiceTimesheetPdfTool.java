package io.jaiclaw.invoice;

import com.taptech.invoice.core.InvoiceData;
import com.taptech.invoice.core.InvoiceResult;
import com.taptech.invoice.core.TimesheetPdfGenerator;
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
 * Generate a PDF timesheet from YAML content or structured fields.
 */
public class InvoiceTimesheetPdfTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "yaml_content": {
                  "type": "string",
                  "description": "Full YAML content for the timesheet"
                },
                "company_name": { "type": "string", "description": "Company name" },
                "client_name": { "type": "string", "description": "Client/employee name" },
                "invoice_number": { "type": "string", "description": "Timesheet number" },
                "invoice_date": { "type": "string", "description": "Date" },
                "invoice_period": { "type": "string", "description": "Period" },
                "include_calendar": {
                  "type": "boolean",
                  "description": "Include a mini month calendar on the PDF (default: false)"
                },
                "line_items": {
                  "type": "array",
                  "description": "Array of line items with date, description, hours",
                  "items": {
                    "type": "object",
                    "properties": {
                      "date": { "type": "string" },
                      "description": { "type": "string" },
                      "hours": { "type": "number" }
                    },
                    "required": ["date", "description", "hours"]
                  }
                }
              }
            }""";

    private final TimesheetPdfGenerator generator;
    private final InvoiceConfig config;

    public InvoiceTimesheetPdfTool(TimesheetPdfGenerator generator, InvoiceConfig config) {
        super(new ToolDefinition(
                "invoice_timesheet_pdf",
                "Generate a PDF timesheet with signature fields from YAML content or structured fields. Optionally includes a mini calendar. Returns the generated PDF file path.",
                ToolCatalog.SECTION_INVOICE,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ));
        this.generator = generator;
        this.config = config;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        InvoiceData data = InvoiceToolUtils.resolveInvoiceData(parameters, config);
        boolean includeCalendar = Boolean.TRUE.equals(parameters.get("include_calendar"));
        Path outputDir = Path.of(config.outputDir());
        Files.createDirectories(outputDir);
        InvoiceResult result = generator.generate(data, outputDir, includeCalendar);

        return new ToolResult.Success(
                result.summary(),
                Map.of("file_path", result.outputFile().toAbsolutePath().toString(),
                       "file_type", result.type(),
                       "file_name", result.outputFile().getFileName().toString())
        );
    }
}
