package io.jaiclaw.scaffold.generators;

import io.jaiclaw.scaffold.ProjectManifest;
import io.jaiclaw.scaffold.ProjectManifest.CustomTool;
import io.jaiclaw.scaffold.ProjectManifest.ToolParameter;

import java.util.Map;

/**
 * Generates @Component ToolCallback stub classes from custom-tools definitions.
 */
public final class ToolStubGenerator {

    private ToolStubGenerator() {}

    public static String generate(ProjectManifest manifest, CustomTool tool) {
        String className = toClassName(tool.name());

        var sb = new StringBuilder();
        sb.append("package ").append(manifest.javaPackage()).append(";\n\n");
        sb.append("import io.jaiclaw.core.tool.ToolCallback;\n");
        sb.append("import io.jaiclaw.core.tool.ToolContext;\n");
        sb.append("import io.jaiclaw.core.tool.ToolDefinition;\n");
        sb.append("import io.jaiclaw.core.tool.ToolResult;\n");
        sb.append("import org.springframework.stereotype.Component;\n\n");
        sb.append("import java.util.Map;\n\n");

        sb.append("/**\n");
        sb.append(" * ").append(tool.description()).append("\n");
        sb.append(" */\n");
        sb.append("@Component\n");
        sb.append("public class ").append(className).append(" implements ToolCallback {\n\n");

        // definition()
        sb.append("    @Override\n");
        sb.append("    public ToolDefinition definition() {\n");
        sb.append("        return new ToolDefinition(\n");
        sb.append("                \"").append(tool.name()).append("\",\n");
        sb.append("                \"").append(escapeJava(tool.description())).append("\",\n");
        sb.append("                \"").append(tool.section()).append("\",\n");
        sb.append("                \"\"\"\n");
        sb.append("                ").append(generateJsonSchema(tool)).append("\n");
        sb.append("                \"\"\"\n");
        sb.append("        );\n");
        sb.append("    }\n\n");

        // execute()
        sb.append("    @Override\n");
        sb.append("    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {\n");
        for (var entry : tool.parameters().entrySet()) {
            String paramName = entry.getKey();
            String javaType = javaType(entry.getValue().type());
            sb.append("        ").append(javaType).append(" ").append(paramName);
            sb.append(" = (").append(javaType).append(") parameters.get(\"").append(paramName).append("\");\n");
        }
        sb.append("\n");
        sb.append("        // TODO: Implement tool logic\n");
        sb.append("        return new ToolResult.Success(\"{\\\"status\\\": \\\"not implemented\\\"}\");\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    public static String toClassName(String toolName) {
        var sb = new StringBuilder();
        for (String part : toolName.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        sb.append("Tool");
        return sb.toString();
    }

    private static String generateJsonSchema(CustomTool tool) {
        var sb = new StringBuilder();
        sb.append("{\n");
        sb.append("                  \"type\": \"object\",\n");
        sb.append("                  \"properties\": {\n");

        var entries = tool.parameters().entrySet().stream().toList();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            sb.append("                    \"").append(entry.getKey()).append("\": { ");
            sb.append("\"type\": \"").append(entry.getValue().type()).append("\"");
            if (entry.getValue().description() != null && !entry.getValue().description().isEmpty()) {
                sb.append(", \"description\": \"").append(escapeJava(entry.getValue().description())).append("\"");
            }
            sb.append(" }");
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("                  }");

        // Required fields
        var required = tool.parameters().entrySet().stream()
                .filter(e -> e.getValue().required())
                .map(Map.Entry::getKey)
                .toList();
        if (!required.isEmpty()) {
            sb.append(",\n                  \"required\": [");
            sb.append(required.stream().map(r -> "\"" + r + "\"").reduce((a, b) -> a + ", " + b).orElse(""));
            sb.append("]");
        }
        sb.append("\n                }");
        return sb.toString();
    }

    private static String javaType(String jsonType) {
        return switch (jsonType) {
            case "integer" -> "Integer";
            case "number" -> "Double";
            case "boolean" -> "Boolean";
            default -> "String";
        };
    }

    private static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
