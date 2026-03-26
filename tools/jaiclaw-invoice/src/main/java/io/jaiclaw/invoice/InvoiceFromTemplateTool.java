package io.jaiclaw.invoice;

import com.taptech.invoice.core.InvoiceData;
import com.taptech.invoice.core.InvoiceGenerator;
import com.taptech.invoice.core.InvoiceResult;
import com.taptech.invoice.core.TimesheetExcelGenerator;
import com.taptech.invoice.core.TimesheetPdfGenerator;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Load a YAML template, optionally override fields, and generate one or more output types.
 */
public class InvoiceFromTemplateTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "template_name": {
                  "type": "string",
                  "description": "YAML template filename (e.g. invoice_001.yaml)"
                },
                "output_types": {
                  "type": "array",
                  "description": "Output types to generate: invoice_pdf, timesheet_excel, timesheet_pdf",
                  "items": {
                    "type": "string",
                    "enum": ["invoice_pdf", "timesheet_excel", "timesheet_pdf"]
                  }
                },
                "overrides": {
                  "type": "object",
                  "description": "Field overrides: company_name, client_name, invoice_number, invoice_date, invoice_period, default_rate, payment_terms, footer_message"
                }
              },
              "required": ["template_name"]
            }""";

    private final InvoiceGenerator invoiceGenerator;
    private final TimesheetExcelGenerator timesheetExcelGenerator;
    private final TimesheetPdfGenerator timesheetPdfGenerator;
    private final InvoiceConfig config;

    public InvoiceFromTemplateTool(InvoiceGenerator invoiceGenerator,
                                   TimesheetExcelGenerator timesheetExcelGenerator,
                                   TimesheetPdfGenerator timesheetPdfGenerator,
                                   InvoiceConfig config) {
        super(new ToolDefinition(
                "invoice_from_template",
                "Load a YAML template from the data directory, apply optional field overrides, and generate invoices/timesheets. Returns paths to all generated files.",
                ToolCatalog.SECTION_INVOICE,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ));
        this.invoiceGenerator = invoiceGenerator;
        this.timesheetExcelGenerator = timesheetExcelGenerator;
        this.timesheetPdfGenerator = timesheetPdfGenerator;
        this.config = config;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String templateName = requireParam(parameters, "template_name");
        Path templatePath = Path.of(config.dataDir(), templateName);

        if (!Files.exists(templatePath)) {
            return new ToolResult.Error("Template not found: " + templatePath);
        }

        // Load template data
        InvoiceData data = InvoiceData.fromYamlFile(templatePath.toString());

        // Apply overrides if provided
        Map<String, Object> overrides = (Map<String, Object>) parameters.get("overrides");
        if (overrides != null && !overrides.isEmpty()) {
            data = InvoiceToolUtils.applyOverrides(data, overrides, config);
        }

        // Determine output types
        List<String> outputTypes = (List<String>) parameters.get("output_types");
        if (outputTypes == null || outputTypes.isEmpty()) {
            outputTypes = List.of("invoice_pdf");
        }

        Path outputDir = Path.of(config.outputDir());
        Files.createDirectories(outputDir);

        StringBuilder summary = new StringBuilder();
        Map<String, Object> metadata = new LinkedHashMap<>();
        List<String> filePaths = new ArrayList<>();

        for (String outputType : outputTypes) {
            InvoiceResult result = switch (outputType) {
                case "invoice_pdf" -> invoiceGenerator.generate(data, outputDir);
                case "timesheet_excel" -> timesheetExcelGenerator.generate(data, outputDir);
                case "timesheet_pdf" -> timesheetPdfGenerator.generate(data, outputDir);
                default -> throw new IllegalArgumentException("Unknown output type: " + outputType);
            };
            summary.append(result.summary()).append("\n");
            filePaths.add(result.outputFile().toAbsolutePath().toString());
        }

        // Use the first file as the primary file metadata
        if (!filePaths.isEmpty()) {
            Path firstFile = Path.of(filePaths.get(0));
            metadata.put("file_path", filePaths.get(0));
            metadata.put("file_name", firstFile.getFileName().toString());
            metadata.put("file_type", "application/pdf");
        }
        if (filePaths.size() > 1) {
            metadata.put("additional_files", filePaths.subList(1, filePaths.size()));
        }

        return new ToolResult.Success(summary.toString().trim(), metadata);
    }
}
