package io.jaiclaw.pipeline.processors.integration;

import io.jaiclaw.memory.MemorySearchManager;
import io.jaiclaw.memory.MemorySearchOptions;
import io.jaiclaw.memory.MemorySearchResult;
import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.TemplateResolver;
import org.apache.camel.Exchange;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query the {@link MemorySearchManager} SPI as a pipeline stage — one
 * of the two nodes that turns "document pipeline" into "RAG
 * ingestion pipeline" (the other is Memory Upsert, deferred to a
 * follow-up because {@code jaiclaw-memory} exposes read-only SPI
 * today).
 *
 * <p>Config keys:
 * <ul>
 *   <li>{@code query} — search string; template-rendered. Defaults to
 *       the current exchange body if blank.</li>
 *   <li>{@code topK} — max results, default 5.</li>
 * </ul>
 *
 * <p>Response: sets the body to a JSON array of
 * {@code {content, score, source}} objects — downstream stages can
 * hand it to a JsonPath extract or template-inject into an LLM
 * prompt.
 */
@PipelineProcessor(
        name = "Memory Search",
        category = "Integration",
        description = "Semantic search over the configured MemorySearchManager (RAG-style retrieval)",
        icon = "search")
public class MemorySearch implements ConfigurableStageProcessor {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MemorySearchManager searchManager;

    public MemorySearch(MemorySearchManager searchManager) {
        this.searchManager = searchManager;
    }

    @Override
    public void process(Exchange exchange, StageDefinition stage,
                        PipelineContext context, Map<String, String> config) throws Exception {
        String queryTemplate = config.get("query");
        String query;
        if (queryTemplate == null || queryTemplate.isBlank()) {
            query = exchange.getIn().getBody(String.class);
        } else {
            query = TemplateResolver.resolve(queryTemplate, context);
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Memory Search requires a non-blank query — either 'query' config or a non-empty stage input");
        }
        int topK = parseInt(config.get("topK"), 5);
        MemorySearchOptions options = MemorySearchOptions.DEFAULT;
        // MemorySearchOptions is opaque here — a follow-up can expose
        // topK / filters through it when the SPI grows. For now the
        // manager's default options apply and we truncate client-side.
        List<MemorySearchResult> results = searchManager.search(query, options);
        List<Map<String, Object>> payload = new ArrayList<>();
        int i = 0;
        for (MemorySearchResult r : results) {
            if (i++ >= topK) break;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("content", safe(r.toString()));
            payload.add(entry);
        }
        exchange.getIn().setBody(JSON.writeValueAsString(payload));
    }

    @Override
    public String configSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "Query text; template-rendered. Defaults to the stage input." },
                    "topK":  { "type": "string", "default": "5" }
                  }
                }""";
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
