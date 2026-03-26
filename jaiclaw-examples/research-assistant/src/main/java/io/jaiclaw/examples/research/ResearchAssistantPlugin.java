package io.jaiclaw.examples.research;

import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Research assistant plugin demonstrating multi-iteration tool loops with
 * context compaction and workspace memory.
 *
 * <p>The agent researches topics by searching sources, fetching articles,
 * saving findings, and generating structured reports. Long conversations
 * trigger context compaction automatically.
 */
@Component
public class ResearchAssistantPlugin implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(ResearchAssistantPlugin.class);

    private final Map<String, List<Finding>> findings = new LinkedHashMap<>();

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "research-assistant-plugin",
                "Research Assistant Plugin",
                "Tools for multi-source research with compaction and memory",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new SearchSourcesTool());
        api.registerTool(new FetchArticleTool());
        api.registerTool(new SaveFindingTool(findings));
        api.registerTool(new GenerateReportTool(findings));

        // Log compaction events
        api.on(HookName.BEFORE_COMPACTION, (event, ctx) -> {
            log.info("[HOOK] BEFORE_COMPACTION: context window approaching limit, compaction starting");
            return null;
        });

        api.on(HookName.AFTER_COMPACTION, (event, ctx) -> {
            log.info("[HOOK] AFTER_COMPACTION: context compacted successfully");
            return null;
        });
    }

    record Finding(String content, String source) {}

    // ---- Tools ----

    static class SearchSourcesTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "search_sources",
                    "Search for sources on a given research topic",
                    "research",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "query": { "type": "string", "description": "Search query" },
                        "max_results": { "type": "integer", "description": "Maximum number of results (default 5)" }
                      },
                      "required": ["query"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String query = (String) parameters.get("query");
            int maxResults = parameters.containsKey("max_results")
                    ? ((Number) parameters.get("max_results")).intValue() : 5;

            var results = new StringBuilder("Search results for: \"%s\"\n\n".formatted(query));
            String[][] mockSources = {
                    {"Advances in %s: A Comprehensive Survey", "https://arxiv.org/abs/2024.%s.001", "This paper presents a systematic review of recent advances in %s, covering methodologies, benchmarks, and open challenges."},
                    {"Practical Applications of %s in Industry", "https://techblog.example.com/%s-industry", "An industry perspective on deploying %s in production systems, with case studies from Fortune 500 companies."},
                    {"%s: State of the Art and Future Directions", "https://journal.example.org/%s-sota", "A forward-looking analysis of %s covering current limitations and promising research directions."},
                    {"Open Source Tools for %s", "https://github.com/topics/%s", "A curated list of open-source libraries and frameworks for building %s systems."},
                    {"The Economics of %s Adoption", "https://economist.example.com/%s", "Economic analysis of %s adoption trends, costs, and ROI across different sectors."}
            };

            int count = Math.min(maxResults, mockSources.length);
            for (int i = 0; i < count; i++) {
                String slug = query.toLowerCase().replaceAll("\\s+", "-");
                results.append("%d. %s\n   URL: %s\n   %s\n\n".formatted(
                        i + 1,
                        mockSources[i][0].formatted(query),
                        mockSources[i][1].formatted(slug),
                        mockSources[i][2].formatted(query)));
            }

            return new ToolResult.Success(results.toString());
        }
    }

    static class FetchArticleTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "fetch_article",
                    "Fetch and extract the content of an article from a URL",
                    "research",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "url": { "type": "string", "description": "URL of the article to fetch" }
                      },
                      "required": ["url"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String url = (String) parameters.get("url");
            return new ToolResult.Success("""
                    Article content from: %s

                    Abstract:
                    This article explores recent developments in the field, examining both theoretical
                    foundations and practical implementations. We analyze data from 150+ organizations
                    and identify key success factors for adoption.

                    Key Findings:
                    1. Organizations that adopt iterative approaches see 40%% faster time-to-value
                    2. Integration with existing systems remains the top challenge (cited by 67%% of respondents)
                    3. Open-source tooling has matured significantly, reducing barriers to entry
                    4. Cross-functional teams outperform siloed implementations by 2.3x

                    Methodology:
                    Mixed-methods study combining quantitative surveys (n=500) with qualitative
                    interviews (n=45) across technology, finance, and healthcare sectors.

                    Conclusion:
                    The field is transitioning from early adoption to mainstream deployment. Success
                    depends on organizational readiness, tool selection, and iterative implementation.
                    """.formatted(url));
        }
    }

    static class SaveFindingTool implements ToolCallback {

        private final Map<String, List<Finding>> findings;

        SaveFindingTool(Map<String, List<Finding>> findings) {
            this.findings = findings;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "save_finding",
                    "Save a research finding for later inclusion in the report",
                    "research",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "topic": { "type": "string", "description": "Topic or category for the finding" },
                        "content": { "type": "string", "description": "The finding content to save" },
                        "source": { "type": "string", "description": "Source URL or reference" }
                      },
                      "required": ["topic", "content", "source"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String topic = (String) parameters.get("topic");
            String content = (String) parameters.get("content");
            String source = (String) parameters.get("source");

            findings.computeIfAbsent(topic, k -> new ArrayList<>()).add(new Finding(content, source));
            int total = findings.values().stream().mapToInt(List::size).sum();

            return new ToolResult.Success(
                    "Finding saved under topic \"%s\". Total findings: %d across %d topics."
                            .formatted(topic, total, findings.size()));
        }
    }

    static class GenerateReportTool implements ToolCallback {

        private final Map<String, List<Finding>> findings;

        GenerateReportTool(Map<String, List<Finding>> findings) {
            this.findings = findings;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "generate_report",
                    "Compile saved findings into a structured research report",
                    "research",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "topic": { "type": "string", "description": "Report title / main topic" },
                        "format": { "type": "string", "enum": ["markdown", "plain"], "description": "Output format" }
                      },
                      "required": ["topic"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String topic = (String) parameters.get("topic");
            String format = parameters.containsKey("format") ? (String) parameters.get("format") : "markdown";
            boolean md = "markdown".equals(format);

            var report = new StringBuilder();
            report.append(md ? "# " : "").append("Research Report: ").append(topic).append("\n\n");

            if (findings.isEmpty()) {
                report.append("No findings saved yet. Use search_sources and save_finding first.\n");
            } else {
                for (var entry : findings.entrySet()) {
                    report.append(md ? "## " : "--- ").append(entry.getKey()).append("\n\n");
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        var f = entry.getValue().get(i);
                        report.append(md ? "- " : "  * ").append(f.content()).append("\n");
                        report.append(md ? "  *Source: " : "    Source: ").append(f.source())
                                .append(md ? "*" : "").append("\n\n");
                    }
                }

                var sources = findings.values().stream()
                        .flatMap(List::stream)
                        .map(Finding::source)
                        .distinct()
                        .collect(Collectors.toList());
                report.append(md ? "## " : "--- ").append("References\n\n");
                for (int i = 0; i < sources.size(); i++) {
                    report.append("%d. %s\n".formatted(i + 1, sources.get(i)));
                }
            }

            return new ToolResult.Success(report.toString());
        }
    }
}
