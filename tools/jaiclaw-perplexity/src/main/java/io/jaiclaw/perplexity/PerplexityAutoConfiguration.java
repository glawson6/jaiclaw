package io.jaiclaw.perplexity;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.perplexity.model.*;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class PerplexityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PerplexityAutoConfiguration.class);

    @Bean
    public PerplexityToolsRegistrar perplexityToolsRegistrar(ToolRegistry toolRegistry) {
        log.info("Registering Perplexity tools into ToolRegistry");
        toolRegistry.register(new PerplexitySearchTool());
        toolRegistry.register(new PerplexityWebSearchTool());
        toolRegistry.register(new PerplexityResearchTool());
        return new PerplexityToolsRegistrar();
    }

    public static class PerplexityToolsRegistrar {}

    // --- Tool implementations ---

    static class PerplexitySearchTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "perplexity_search",
                    "Search the web with Perplexity AI and get cited answers. Returns an LLM-synthesized answer with source citations.",
                    "perplexity",
                    """
                    {"type":"object","properties":{"query":{"type":"string","description":"The search query"},"model":{"type":"string","description":"Model to use (default: sonar-pro)"},"domains":{"type":"string","description":"Comma-separated domain filter"},"recency":{"type":"string","description":"Recency filter: month, week, day, hour"},"images":{"type":"boolean","description":"Include images in results"}},"required":["query"]}
                    """,
                    Set.of(ToolProfile.FULL)
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            try {
                PerplexityClient client = createClient();
                String query = (String) parameters.get("query");
                String model = (String) parameters.getOrDefault("model", "sonar-pro");
                String domains = (String) parameters.get("domains");
                String recency = (String) parameters.get("recency");
                boolean images = Boolean.TRUE.equals(parameters.get("images"));

                SonarRequest request = SonarRequest.builder()
                        .model(model)
                        .messages(List.of(new Message("user", query)))
                        .temperature(0.2)
                        .maxTokens(4096)
                        .searchDomainFilter(parseDomains(domains))
                        .searchRecencyFilter(recency)
                        .returnImages(images)
                        .returnRelatedQuestions(true)
                        .build();

                SonarResponse response = client.chat(request);
                return new ToolResult.Success(formatSonarResult(response));
            } catch (PerplexityApiException e) {
                return new ToolResult.Error("Perplexity API error (%d): %s".formatted(e.getStatusCode(), e.getMessage()));
            } catch (Exception e) {
                return new ToolResult.Error("Perplexity search failed: " + e.getMessage());
            }
        }
    }

    static class PerplexityWebSearchTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "perplexity_web_search",
                    "Raw web search returning ranked results without LLM synthesis. Use for factual lookups where you want raw search results.",
                    "perplexity",
                    """
                    {"type":"object","properties":{"query":{"type":"string","description":"The search query"},"numResults":{"type":"integer","description":"Number of results (default: 10)"},"recency":{"type":"string","description":"Recency filter: month, week, day, hour"},"domains":{"type":"string","description":"Comma-separated domain filter"}},"required":["query"]}
                    """,
                    Set.of(ToolProfile.FULL)
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            try {
                PerplexityClient client = createClient();
                String query = (String) parameters.get("query");
                Integer numResults = parameters.get("numResults") instanceof Number n ? n.intValue() : 10;
                String recency = (String) parameters.get("recency");
                String domains = (String) parameters.get("domains");

                SearchApiRequest request = new SearchApiRequest(query, numResults, recency, parseDomains(domains));
                SearchApiResponse response = client.search(request);

                return new ToolResult.Success(formatSearchResult(response));
            } catch (PerplexityApiException e) {
                return new ToolResult.Error("Perplexity API error (%d): %s".formatted(e.getStatusCode(), e.getMessage()));
            } catch (Exception e) {
                return new ToolResult.Error("Perplexity web search failed: " + e.getMessage());
            }
        }
    }

    static class PerplexityResearchTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "perplexity_research",
                    "Deep multi-step research on complex topics using the Perplexity Agent API. Best for questions requiring thorough investigation.",
                    "perplexity",
                    """
                    {"type":"object","properties":{"query":{"type":"string","description":"The research question"},"preset":{"type":"string","description":"Research depth: fast-search, pro-search, deep-research, advanced"},"model":{"type":"string","description":"Model override"}},"required":["query"]}
                    """,
                    Set.of(ToolProfile.FULL)
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            try {
                PerplexityClient client = createClient();
                String query = (String) parameters.get("query");
                String preset = (String) parameters.getOrDefault("preset", "pro-search");
                String model = (String) parameters.get("model");

                AgentRequest request = AgentRequest.builder()
                        .preset(preset)
                        .model(model)
                        .messages(List.of(new Message("user", query)))
                        .maxTokens(4096)
                        .build();

                AgentResponse response = client.agent(request);
                return new ToolResult.Success(formatAgentResult(response));
            } catch (PerplexityApiException e) {
                return new ToolResult.Error("Perplexity API error (%d): %s".formatted(e.getStatusCode(), e.getMessage()));
            } catch (Exception e) {
                return new ToolResult.Error("Perplexity research failed: " + e.getMessage());
            }
        }
    }

    // --- Shared helpers ---

    private static PerplexityClient createClient() {
        String apiKey = System.getenv("PERPLEXITY_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("PERPLEXITY_API_KEY environment variable not set");
        }
        return new PerplexityClient(apiKey);
    }

    private static List<String> parseDomains(String domains) {
        if (domains == null || domains.isBlank()) return null;
        return Arrays.stream(domains.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String formatSonarResult(SonarResponse response) {
        var sb = new StringBuilder();
        if (response.choices() != null && !response.choices().isEmpty()) {
            sb.append(response.choices().getFirst().message().content());
        }
        if (response.citations() != null && !response.citations().isEmpty()) {
            sb.append("\n\nSources:\n");
            for (int i = 0; i < response.citations().size(); i++) {
                sb.append("[%d] %s\n".formatted(i + 1, response.citations().get(i)));
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String formatSearchResult(SearchApiResponse response) {
        var sb = new StringBuilder();
        if (response.results() != null) {
            for (int i = 0; i < response.results().size(); i++) {
                SearchResult r = response.results().get(i);
                sb.append("%d. %s\n   %s\n".formatted(i + 1,
                        r.title() != null ? r.title() : "Untitled", r.url()));
                if (r.snippet() != null) sb.append("   %s\n".formatted(r.snippet()));
                sb.append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String formatAgentResult(AgentResponse response) {
        var sb = new StringBuilder();
        if (response.content() != null) {
            sb.append(response.content());
        }
        if (response.citations() != null && !response.citations().isEmpty()) {
            sb.append("\n\nSources:\n");
            for (int i = 0; i < response.citations().size(); i++) {
                Citation c = response.citations().get(i);
                sb.append("[%d] %s — %s\n".formatted(i + 1,
                        c.title() != null ? c.title() : "Source", c.url()));
            }
        }
        return sb.toString().stripTrailing();
    }
}
