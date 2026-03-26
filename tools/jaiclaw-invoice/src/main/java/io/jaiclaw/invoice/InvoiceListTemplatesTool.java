package io.jaiclaw.invoice;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * List available YAML invoice/timesheet templates in the data directory.
 */
public class InvoiceListTemplatesTool extends AbstractBuiltinTool {

    private final InvoiceConfig config;

    public InvoiceListTemplatesTool(InvoiceConfig config) {
        super(new ToolDefinition(
                "invoice_list_templates",
                "List available YAML invoice/timesheet templates in the configured data directory. Returns template names that can be used with invoice_from_template.",
                ToolCatalog.SECTION_INVOICE,
                """
                {"type":"object","properties":{},"required":[]}""",
                Set.of(ToolProfile.FULL)
        ));
        this.config = config;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        Path dataDir = Path.of(config.dataDir());
        if (!Files.isDirectory(dataDir)) {
            return new ToolResult.Success("No data directory found at: " + dataDir);
        }

        try (Stream<Path> files = Files.list(dataDir)) {
            String templates = files
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.joining("\n"));

            if (templates.isEmpty()) {
                return new ToolResult.Success("No YAML templates found in: " + dataDir);
            }

            return new ToolResult.Success("Available templates in " + dataDir + ":\n" + templates);
        }
    }
}
