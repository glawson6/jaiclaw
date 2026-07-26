package io.jaiclaw.documents.tools;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.documents.PdfFormFiller;
import io.jaiclaw.documents.PdfFormResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tool that fills a PDF form with provided field values and writes the result.
 *
 * <p>Input:
 * <pre>{
 *   "templatePath": "/path/to/template.pdf",
 *   "outputPath": "/path/to/output.pdf",
 *   "fields": {"fieldName": "value", ...},
 *   "flatten": true
 * }</pre>
 *
 * <p>Output: JSON with fieldsSet count, skippedFields list, and outputPath.
 */
public class PdfFillFormTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "templatePath": {
                  "type": "string",
                  "description": "Absolute path to the PDF template file"
                },
                "outputPath": {
                  "type": "string",
                  "description": "Absolute path where the filled PDF should be written"
                },
                "fields": {
                  "type": "object",
                  "description": "Map of PDF field names to values to fill in",
                  "additionalProperties": { "type": "string" }
                },
                "flatten": {
                  "type": "boolean",
                  "description": "Whether to flatten the form after filling (default: true)"
                }
              },
              "required": ["templatePath", "outputPath", "fields"]
            }""";

    private final ObjectMapper mapper = new ObjectMapper();

    public PdfFillFormTool() {
        super(new ToolDefinition(
                "pdf_fill_form",
                "Fill a PDF form with field values and write the result to a file. Returns a summary of filled, skipped, and output path.",
                ToolCatalog.SECTION_DOCUMENTS,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String templatePathStr = requireParam(parameters, "templatePath");
        String outputPathStr = requireParam(parameters, "outputPath");
        Object fieldsObj = parameters.get("fields");
        if (fieldsObj == null) {
            return new ToolResult.Error("Missing required parameter: fields");
        }

        boolean flatten = true;
        if (parameters.containsKey("flatten")) {
            Object flattenObj = parameters.get("flatten");
            if (flattenObj instanceof Boolean b) {
                flatten = b;
            } else {
                flatten = Boolean.parseBoolean(flattenObj.toString());
            }
        }

        Path templatePath = Path.of(templatePathStr);
        if (!Files.exists(templatePath)) {
            return new ToolResult.Error("Template file not found: " + templatePathStr);
        }
        if (!Files.isRegularFile(templatePath)) {
            return new ToolResult.Error("Not a regular file: " + templatePathStr);
        }

        Map<String, String> fieldValues = new LinkedHashMap<>();
        if (fieldsObj instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                fieldValues.put(entry.getKey().toString(),
                        entry.getValue() != null ? entry.getValue().toString() : "");
            }
        } else {
            return new ToolResult.Error("Parameter 'fields' must be a JSON object");
        }

        byte[] templateBytes = Files.readAllBytes(templatePath);
        PdfFormFiller filler = new PdfFormFiller(flatten);
        PdfFormResult result = filler.fill(templateBytes, fieldValues);

        if (result instanceof PdfFormResult.Success success) {
            Path outputPath = Path.of(outputPathStr);
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, success.pdfBytes());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("fieldsSet", success.fieldsSet());
            response.put("skippedFields", success.skippedFields());
            response.put("outputPath", outputPath.toAbsolutePath().toString());

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            return new ToolResult.Success(json);
        } else if (result instanceof PdfFormResult.Failure failure) {
            return new ToolResult.Error(failure.reason());
        }

        return new ToolResult.Error("Unexpected result type");
    }
}
