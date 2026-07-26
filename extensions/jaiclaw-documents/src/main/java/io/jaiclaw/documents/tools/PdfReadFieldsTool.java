package io.jaiclaw.documents.tools;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.documents.PdfFormField;
import io.jaiclaw.documents.PdfFormReader;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool that reads PDF form fields and returns their metadata as JSON.
 *
 * <p>Input: {@code {"path": "/path/to/form.pdf"}}
 * <p>Output: JSON array of field descriptors with name, type, currentValue, and options.
 */
public class PdfReadFieldsTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Absolute path to the PDF file to inspect"
                }
              },
              "required": ["path"]
            }""";

    private final PdfFormReader pdfFormReader;
    private final ObjectMapper mapper = new ObjectMapper();

    public PdfReadFieldsTool() {
        this(new PdfFormReader());
    }

    public PdfReadFieldsTool(PdfFormReader pdfFormReader) {
        super(new ToolDefinition(
                "pdf_read_fields",
                "Read a PDF form's fillable fields and return their metadata (name, type, current value, valid options).",
                ToolCatalog.SECTION_DOCUMENTS,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
        this.pdfFormReader = pdfFormReader;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String pathStr = requireParam(parameters, "path");
        Path pdfPath = Path.of(pathStr);

        if (!Files.exists(pdfPath)) {
            return new ToolResult.Error("File not found: " + pathStr);
        }
        if (!Files.isRegularFile(pdfPath)) {
            return new ToolResult.Error("Not a regular file: " + pathStr);
        }

        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        List<PdfFormField> fields = pdfFormReader.readFields(pdfBytes);

        if (fields.isEmpty()) {
            return new ToolResult.Success("No fillable form fields found in: " + pathStr);
        }

        List<Map<String, Object>> fieldList = fields.stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", f.name());
                    m.put("type", f.type().name());
                    m.put("currentValue", f.currentValue() != null ? f.currentValue() : "");
                    if (!f.options().isEmpty()) {
                        m.put("options", f.options());
                    }
                    return m;
                })
                .toList();

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(fieldList);
        return new ToolResult.Success(json);
    }
}
