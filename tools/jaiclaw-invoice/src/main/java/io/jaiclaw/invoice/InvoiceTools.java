package io.jaiclaw.invoice;

import com.taptech.invoice.core.InvoiceGenerator;
import com.taptech.invoice.core.TimesheetExcelGenerator;
import com.taptech.invoice.core.TimesheetPdfGenerator;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for creating and registering all invoice tools.
 */
public final class InvoiceTools {

    private InvoiceTools() {}

    public static List<ToolCallback> all(InvoiceConfig config) {
        InvoiceGenerator invoiceGen = new InvoiceGenerator();
        TimesheetExcelGenerator excelGen = new TimesheetExcelGenerator();
        TimesheetPdfGenerator pdfGen = new TimesheetPdfGenerator();

        return List.of(
                new InvoiceGenerateTool(invoiceGen, config),
                new InvoiceTimesheetExcelTool(excelGen, config),
                new InvoiceTimesheetPdfTool(pdfGen, config),
                new InvoiceListTemplatesTool(config),
                new InvoiceFromTemplateTool(invoiceGen, excelGen, pdfGen, config)
        );
    }

    public static void registerAll(ToolRegistry registry, InvoiceConfig config) {
        registry.registerAll(all(config));
    }
}
