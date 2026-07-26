package io.jaiclaw.pipeline.processors.integration;

import io.jaiclaw.documents.DocumentParser;
import io.jaiclaw.documents.ParsedDocument;
import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import org.apache.camel.Exchange;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Front door for file-triggered pipelines: parse PDF / HTML / plain-text
 * bytes into plain text so downstream AGENT stages have a clean prompt
 * input. Delegates to whatever {@link DocumentParser} is on the
 * classpath (typically a {@code CompositeDocumentParser} routing PDF →
 * PDFBox and everything else → the plain-text impl).
 *
 * <p>The stage input can be either:
 * <ul>
 *   <li>a raw byte array (Camel binary body) — passed straight through
 *       to the parser, or</li>
 *   <li>a base64-encoded string when {@code inputEncoding=base64} — the
 *       processor decodes before parsing, matching the shape a
 *       preceding HTTP Fetch of a binary would produce.</li>
 * </ul>
 *
 * <p>The MIME type is taken from the {@code mimeType} config (falls back
 * to {@code text/plain}).
 */
@PipelineProcessor(
        name = "Document Parse",
        category = "Integration",
        description = "Parse PDF / HTML / text bytes into plain text via the DocumentParser SPI",
        icon = "file-text")
public class DocumentParseProcessor implements ConfigurableStageProcessor {

    private final DocumentParser parser;

    public DocumentParseProcessor(DocumentParser parser) {
        this.parser = parser;
    }

    @Override
    public void process(Exchange exchange, StageDefinition stage,
                        PipelineContext context, Map<String, String> config) {
        String mimeType = config.getOrDefault("mimeType", "text/plain");
        String inputEncoding = config.getOrDefault("inputEncoding", "auto");
        byte[] bytes = resolveBytes(exchange, inputEncoding);
        ParsedDocument parsed = parser.parse(bytes, mimeType);
        exchange.getIn().setBody(parsed.text() == null ? "" : parsed.text());
    }

    @Override
    public String configSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "mimeType":      { "type": "string", "default": "text/plain" },
                    "inputEncoding": { "type": "string", "enum": ["auto", "base64", "raw"], "default": "auto" }
                  }
                }""";
    }

    private static byte[] resolveBytes(Exchange exchange, String encoding) {
        Object body = exchange.getIn().getBody();
        if (body instanceof byte[] raw) return raw;
        String s = body == null ? "" : body.toString();
        return switch (encoding.toLowerCase()) {
            case "base64" -> Base64.getDecoder().decode(s);
            case "raw"    -> s.getBytes(StandardCharsets.UTF_8);
            default       -> tryBase64ThenRaw(s);
        };
    }

    private static byte[] tryBase64ThenRaw(String s) {
        // auto-detect: base64 alphabet only? decode; else UTF-8 raw.
        if (s.matches("^[A-Za-z0-9+/=\\s]+$") && s.length() > 8) {
            try {
                return Base64.getDecoder().decode(s.replaceAll("\\s+", ""));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
