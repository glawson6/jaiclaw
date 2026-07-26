package io.jaiclaw.pipeline.processors.data;

import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.TemplateResolver;
import org.apache.camel.Exchange;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP fetch using the JDK client — a "small safe alternative to a
 * raw CAMEL stage" per the analysis. Restricted to {@code http} and
 * {@code https} schemes; the pipeline-level URI-scheme allowlist
 * (Phase 3, {@code PipelineSecurityProperties.allowedUriSchemes})
 * governs raw {@code CAMEL} stages, this processor is a self-contained
 * palette-friendly node.
 *
 * <p>Config keys:
 * <ul>
 *   <li>{@code url} — template-rendered; must be a valid absolute URL
 *       with an {@code http} or {@code https} scheme.</li>
 *   <li>{@code method} — {@code GET} (default) or {@code POST}.</li>
 *   <li>{@code headers} — {@code Key: Value} pairs separated by newlines
 *       (each header line supports template substitution).</li>
 *   <li>{@code body} — request body for POST; template-rendered.</li>
 *   <li>{@code timeoutSeconds} — connect + response timeout, default 30.</li>
 * </ul>
 *
 * <p>Response: sets the exchange body to the response body string.
 * Non-2xx status throws {@code IllegalStateException} — trips the
 * pipeline's error strategy.
 */
@PipelineProcessor(
        name = "HTTP Fetch",
        category = "Data",
        description = "GET/POST an HTTP endpoint and put the response body on the exchange",
        icon = "network")
public class HttpFetch implements ConfigurableStageProcessor {

    @Override
    public void process(Exchange exchange, StageDefinition stage,
                        PipelineContext context, Map<String, String> config) throws Exception {
        String urlTemplate = config.get("url");
        if (urlTemplate == null || urlTemplate.isBlank()) {
            throw new IllegalArgumentException("HTTP Fetch requires 'url' config");
        }
        String url = TemplateResolver.resolve(urlTemplate, context);
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(
                    "HTTP Fetch URL must be http or https: got " + url);
        }
        String method = config.getOrDefault("method", "GET").toUpperCase();
        int timeout = parseInt(config.get("timeoutSeconds"), 30);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeout));

        String headersRaw = config.get("headers");
        if (headersRaw != null && !headersRaw.isBlank()) {
            for (String line : headersRaw.split("\\r?\\n")) {
                int colon = line.indexOf(':');
                if (colon <= 0 || colon == line.length() - 1) continue;
                String name = line.substring(0, colon).trim();
                String value = TemplateResolver.resolve(line.substring(colon + 1).trim(), context);
                if (!name.isEmpty()) builder.header(name, value);
            }
        }

        String body = config.getOrDefault("body", "");
        String renderedBody = TemplateResolver.resolve(body, context);
        HttpRequest request = switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(renderedBody)).build();
            case "PUT"  -> builder.PUT(HttpRequest.BodyPublishers.ofString(renderedBody)).build();
            default     -> builder.GET().build();
        };

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                    "HTTP Fetch " + method + " " + url + " → " + status + ": "
                            + truncate(response.body(), 200));
        }
        exchange.getIn().setBody(response.body());
    }

    @Override
    public String configSchema() {
        return """
                {
                  "type": "object",
                  "required": ["url"],
                  "properties": {
                    "url":            { "type": "string", "description": "http:// or https:// URL (template-rendered)" },
                    "method":         { "type": "string", "enum": ["GET", "POST", "PUT"], "default": "GET" },
                    "headers":        { "type": "string", "description": "One 'Name: Value' per line" },
                    "body":           { "type": "string", "description": "Request body for POST/PUT (template-rendered)" },
                    "timeoutSeconds": { "type": "string", "default": "30" }
                  }
                }""";
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
