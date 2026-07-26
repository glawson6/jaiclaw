package io.jaiclaw.pipeline.processors.transform;

import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import org.apache.camel.Exchange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;
import tools.jackson.dataformat.xml.XmlMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Format converters: JSON ↔ CSV, XML → JSON, HTML → text-like
 * (dumb-stripping is fine for LLM handoffs; adopters wanting Jsoup
 * quality can plug their own). Markdown → HTML is fudged via a
 * minimal in-house renderer to avoid the flexmark dep for the
 * baseline pack.
 */
public final class FormatProcessors {

    private FormatProcessors() {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final XmlMapper XML = new XmlMapper();
    private static final CsvMapper CSV = new CsvMapper();

    @PipelineProcessor(
            name = "JSON ↔ CSV",
            category = "Transform",
            description = "Convert between JSON array-of-objects and CSV; direction=jsonToCsv or csvToJson",
            icon = "table")
    public static class JsonCsv implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) throws Exception {
            String direction = config.getOrDefault("direction", "jsonToCsv");
            String input = exchange.getIn().getBody(String.class);
            if (input == null) input = "";
            String delim = config.getOrDefault("delimiter", ",");
            boolean withHeaders = Boolean.parseBoolean(config.getOrDefault("headers", "true"));

            if ("jsonToCsv".equalsIgnoreCase(direction)) {
                JsonNode root = JSON.readTree(input);
                if (!(root instanceof ArrayNode array) || array.isEmpty()) {
                    exchange.getIn().setBody("");
                    return;
                }
                // Header order: first row's field order.
                List<String> columns = new ArrayList<>();
                array.get(0).properties().forEach(e -> columns.add(e.getKey()));
                CsvSchema.Builder schemaBuilder = CsvSchema.builder();
                for (String c : columns) schemaBuilder.addColumn(c);
                CsvSchema schema = schemaBuilder.build()
                        .withColumnSeparator(delim.charAt(0))
                        .withHeader();
                List<Map<String, Object>> rows = new ArrayList<>();
                array.forEach(node -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    node.properties().forEach(e ->
                            row.put(e.getKey(), e.getValue().asString()));
                    rows.add(row);
                });
                String csv = CSV.writer(withHeaders ? schema : schema.withoutHeader())
                        .writeValueAsString(rows);
                exchange.getIn().setBody(csv);
            } else {
                CsvSchema schema = withHeaders
                        ? CsvSchema.emptySchema().withHeader().withColumnSeparator(delim.charAt(0))
                        : CsvSchema.emptySchema().withColumnSeparator(delim.charAt(0));
                List<Map<String, String>> rows = CSV.readerFor(Map.class)
                        .with(schema)
                        .<Map<String, String>>readValues(input)
                        .readAll();
                exchange.getIn().setBody(JSON.writeValueAsString(rows));
            }
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "properties": {
                        "direction": { "type": "string", "enum": ["jsonToCsv", "csvToJson"], "default": "jsonToCsv" },
                        "delimiter": { "type": "string", "default": ",", "maxLength": 1 },
                        "headers":   { "type": "string", "default": "true" }
                      }
                    }""";
        }
    }

    @PipelineProcessor(
            name = "XML → JSON",
            category = "Transform",
            description = "Parse an XML document and serialise the tree as JSON",
            icon = "code")
    public static class XmlToJson implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) throws Exception {
            String input = exchange.getIn().getBody(String.class);
            if (input == null || input.isBlank()) {
                exchange.getIn().setBody("{}");
                return;
            }
            JsonNode tree = XML.readTree(input);
            exchange.getIn().setBody(JSON.writeValueAsString(tree));
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "properties": {}
                    }""";
        }
    }

    @PipelineProcessor(
            name = "HTML → Text",
            category = "Transform",
            description = "Strip HTML tags, decode entities, and collapse whitespace",
            icon = "text")
    public static class HtmlToText implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) {
            String input = exchange.getIn().getBody(String.class);
            if (input == null) input = "";
            // Very simple: drop <script>/<style>, drop tags, decode
            // the four common entities, collapse whitespace. Good
            // enough as an LLM prep step.
            String noScript = input.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", " ");
            String noTags = noScript.replaceAll("<[^>]+>", " ");
            String decoded = noTags
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'");
            String collapsed = decoded.replaceAll("\\s+", " ").trim();
            exchange.getIn().setBody(collapsed);
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "properties": {}
                    }""";
        }
    }

    @PipelineProcessor(
            name = "Markdown → HTML",
            category = "Transform",
            description = "Tiny in-house markdown-to-HTML converter — headings, bold, italic, code, links, paragraphs",
            icon = "markdown")
    public static class MarkdownToHtml implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) {
            String input = exchange.getIn().getBody(String.class);
            if (input == null) input = "";
            StringBuilder out = new StringBuilder();
            String[] lines = input.split("\\r?\\n");
            boolean inParagraph = false;
            for (String line : lines) {
                if (line.isBlank()) {
                    if (inParagraph) { out.append("</p>\n"); inParagraph = false; }
                    continue;
                }
                String stripped = line;
                // Headings
                if (stripped.startsWith("### ")) {
                    if (inParagraph) { out.append("</p>\n"); inParagraph = false; }
                    out.append("<h3>").append(inline(stripped.substring(4))).append("</h3>\n");
                } else if (stripped.startsWith("## ")) {
                    if (inParagraph) { out.append("</p>\n"); inParagraph = false; }
                    out.append("<h2>").append(inline(stripped.substring(3))).append("</h2>\n");
                } else if (stripped.startsWith("# ")) {
                    if (inParagraph) { out.append("</p>\n"); inParagraph = false; }
                    out.append("<h1>").append(inline(stripped.substring(2))).append("</h1>\n");
                } else {
                    if (!inParagraph) { out.append("<p>"); inParagraph = true; }
                    else out.append(' ');
                    out.append(inline(stripped));
                }
            }
            if (inParagraph) out.append("</p>\n");
            exchange.getIn().setBody(out.toString().trim());
        }

        private static String inline(String s) {
            // **bold** → <strong>bold</strong>
            String out = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
            // *italic* → <em>italic</em>
            out = out.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
            // `code` → <code>code</code>
            out = out.replaceAll("`([^`]+?)`", "<code>$1</code>");
            // [label](url) → <a href="url">label</a>
            out = out.replaceAll("\\[([^\\]]+?)\\]\\(([^)]+?)\\)", "<a href=\"$2\">$1</a>");
            return out;
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "properties": {}
                    }""";
        }
    }

    @PipelineProcessor(
            name = "Chunk / Split",
            category = "Transform",
            description = "Split the input into fixed-size character chunks with optional overlap",
            icon = "split")
    public static class Chunk implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) {
            int maxSize = parseInt(config.get("maxSize"), 1000);
            int overlap = Math.max(0, Math.min(parseInt(config.get("overlap"), 0), maxSize - 1));
            String input = exchange.getIn().getBody(String.class);
            if (input == null) input = "";
            if (input.length() <= maxSize) {
                exchange.getIn().setBody(input);
                return;
            }
            StringBuilder out = new StringBuilder();
            int step = maxSize - overlap;
            int i = 0;
            int chunkIndex = 0;
            while (i < input.length()) {
                int end = Math.min(i + maxSize, input.length());
                if (chunkIndex > 0) out.append("\n---chunk---\n");
                out.append(input, i, end);
                chunkIndex++;
                if (end == input.length()) break;
                i += step;
            }
            exchange.getIn().setBody(out.toString());
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "properties": {
                        "maxSize": { "type": "string", "default": "1000", "description": "Max chars per chunk" },
                        "overlap": { "type": "string", "default": "0",    "description": "Chars carried over between chunks" }
                      }
                    }""";
        }

        private static int parseInt(String raw, int fallback) {
            if (raw == null || raw.isBlank()) return fallback;
            try { return Integer.parseInt(raw.trim()); }
            catch (NumberFormatException e) { return fallback; }
        }
    }
}
