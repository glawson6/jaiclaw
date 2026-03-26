package io.jaiclaw.perplexity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.jaiclaw.perplexity.model.*;
import io.jaiclaw.perplexity.render.TerminalImageRenderer;
import org.jline.reader.LineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ShellComponent
public class PerplexityCommands {

    private static final Logger log = LoggerFactory.getLogger(PerplexityCommands.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ObjectProvider<LineReader> lineReaderProvider;
    private final TerminalImageRenderer imageRenderer = new TerminalImageRenderer();

    @Value("${perplexity.api-key:#{null}}")
    private String apiKey;

    @Value("${perplexity.default-model:sonar-pro}")
    private String defaultModel;

    @Value("${perplexity.default-preset:pro-search}")
    private String defaultPreset;

    @Value("${perplexity.max-tokens:4096}")
    private int maxTokens;

    @Value("${perplexity.temperature:0.2}")
    private double temperature;

    @Value("${perplexity.images-enabled:false}")
    private boolean imagesEnabled;

    public PerplexityCommands(ObjectProvider<LineReader> lineReaderProvider) {
        this.lineReaderProvider = lineReaderProvider;
    }

    @ShellMethod(value = "Query Perplexity AI with a one-shot question", key = {"pplx ask", "pplx-ask"})
    public String ask(
            @ShellOption(defaultValue = ShellOption.NULL) String query,
            @ShellOption(value = "--model", defaultValue = ShellOption.NULL) String model,
            @ShellOption(value = "--domains", defaultValue = ShellOption.NULL) String domains,
            @ShellOption(value = "--recency", defaultValue = ShellOption.NULL) String recency,
            @ShellOption(value = "--images", defaultValue = "false") boolean images,
            @ShellOption(value = "--format", defaultValue = "text") String format) {

        if (query == null || query.isBlank()) return "Usage: pplx ask <query> [--model ...] [--domains ...] [--recency ...] [--images] [--format json|text]";

        PerplexityClient client = createClient();
        SonarRequest request = SonarRequest.builder()
                .model(model != null ? model : defaultModel)
                .messages(List.of(new Message("user", query)))
                .temperature(temperature)
                .maxTokens(maxTokens)
                .searchDomainFilter(parseDomains(domains))
                .searchRecencyFilter(recency)
                .returnImages(images || imagesEnabled)
                .returnRelatedQuestions(true)
                .build();

        SonarResponse response = client.chat(request);

        if ("json".equalsIgnoreCase(format)) {
            return toJson(response);
        }

        return formatSonarResponse(response, images || imagesEnabled);
    }

    @ShellMethod(value = "Raw web search via Perplexity Search API", key = {"pplx search", "pplx-search"})
    public String search(
            @ShellOption(defaultValue = ShellOption.NULL) String query,
            @ShellOption(value = "--num-results", defaultValue = "10") int numResults,
            @ShellOption(value = "--recency", defaultValue = ShellOption.NULL) String recency,
            @ShellOption(value = "--domains", defaultValue = ShellOption.NULL) String domains) {

        if (query == null || query.isBlank()) return "Usage: pplx search <query> [--num-results ...] [--recency ...] [--domains ...]";

        PerplexityClient client = createClient();
        SearchApiRequest request = new SearchApiRequest(query, numResults, recency, parseDomains(domains));
        SearchApiResponse response = client.search(request);

        return formatSearchResponse(response);
    }

    @ShellMethod(value = "Deep research via Perplexity Agent API", key = {"pplx research", "pplx-research"})
    public String research(
            @ShellOption(defaultValue = ShellOption.NULL) String query,
            @ShellOption(value = "--preset", defaultValue = ShellOption.NULL) String preset,
            @ShellOption(value = "--model", defaultValue = ShellOption.NULL) String model) {

        if (query == null || query.isBlank()) return "Usage: pplx research <query> [--preset fast|pro|deep|advanced] [--model ...]";

        PerplexityClient client = createClient();
        AgentRequest request = AgentRequest.builder()
                .preset(preset != null ? preset : defaultPreset)
                .model(model)
                .messages(List.of(new Message("user", query)))
                .maxTokens(maxTokens)
                .build();

        AgentResponse response = client.agent(request);

        return formatAgentResponse(response);
    }

    @ShellMethod(value = "Enter interactive multi-turn chat mode", key = {"pplx chat", "pplx-chat"})
    public String chat(
            @ShellOption(value = "--model", defaultValue = ShellOption.NULL) String model,
            @ShellOption(value = "--system", defaultValue = ShellOption.NULL) String systemPrompt) {

        LineReader reader = lineReaderProvider.getIfAvailable();
        if (reader == null) {
            return "Interactive chat requires a terminal with LineReader support.";
        }

        PerplexityClient client = createClient();
        String chatModel = model != null ? model : defaultModel;
        List<Message> history = new ArrayList<>();
        boolean showImages = imagesEnabled;

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(new Message("system", systemPrompt));
        }

        System.out.println("Perplexity Chat (model: " + chatModel + "). Type /exit to quit, /clear to reset, /images on|off, /model <name>");
        System.out.println();

        while (true) {
            String input;
            try {
                input = reader.readLine("pplx> ");
            } catch (Exception e) {
                break;
            }

            if (input == null || "/exit".equals(input.trim())) {
                break;
            }

            String trimmed = input.trim();
            if (trimmed.isEmpty()) continue;

            if ("/clear".equals(trimmed)) {
                history.clear();
                if (systemPrompt != null) history.add(new Message("system", systemPrompt));
                System.out.println("Chat history cleared.");
                continue;
            }
            if (trimmed.startsWith("/images ")) {
                showImages = "on".equalsIgnoreCase(trimmed.substring(8).trim());
                System.out.println("Images " + (showImages ? "enabled" : "disabled"));
                continue;
            }
            if (trimmed.startsWith("/model ")) {
                chatModel = trimmed.substring(7).trim();
                System.out.println("Model set to: " + chatModel);
                continue;
            }

            history.add(new Message("user", trimmed));

            try {
                SonarRequest request = SonarRequest.builder()
                        .model(chatModel)
                        .messages(List.copyOf(history))
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .returnImages(showImages)
                        .stream(true)
                        .build();

                System.out.println();
                StringBuilder fullResponse = new StringBuilder();
                client.chatStream(request).forEach(token -> {
                    System.out.print(token);
                    fullResponse.append(token);
                });
                System.out.println("\n");

                history.add(new Message("assistant", fullResponse.toString()));
            } catch (PerplexityApiException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        return "Chat ended.";
    }

    @ShellMethod(value = "List available Perplexity models and presets", key = {"pplx models", "pplx-models"})
    public String models() {
        return """
                Sonar Models (chat/completions):
                  sonar              — Fast, lightweight search model
                  sonar-pro          — Advanced search model (default)
                  sonar-reasoning    — Chain-of-thought reasoning model
                  sonar-reasoning-pro— Advanced reasoning model

                Agent Presets (agent API):
                  fast-search    — Quick single-step search
                  pro-search     — Multi-step professional search (default)
                  deep-research  — Deep multi-source research
                  advanced       — Most thorough research mode
                """;
    }

    @ShellMethod(value = "Show current Perplexity configuration", key = {"pplx config", "pplx-config"})
    public String config() {
        return """
                Perplexity Configuration:
                  API Key:       %s
                  Default Model: %s
                  Default Preset:%s
                  Max Tokens:    %d
                  Temperature:   %.1f
                  Images:        %s
                """.formatted(
                apiKey != null && !apiKey.isBlank() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "(not set)",
                defaultModel,
                defaultPreset,
                maxTokens,
                temperature,
                imagesEnabled ? "enabled" : "disabled"
        );
    }

    private PerplexityClient createClient() {
        String key = resolveApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("PERPLEXITY_API_KEY not set. Set it as an environment variable or in application.yml");
        }
        return new PerplexityClient(key);
    }

    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) return apiKey;
        return System.getenv("PERPLEXITY_API_KEY");
    }

    private String formatSonarResponse(SonarResponse response, boolean renderImages) {
        var sb = new StringBuilder();

        if (response.choices() != null && !response.choices().isEmpty()) {
            sb.append(response.choices().getFirst().message().content());
        }

        if (response.citations() != null && !response.citations().isEmpty()) {
            sb.append("\n\nSources:\n");
            for (int i = 0; i < response.citations().size(); i++) {
                sb.append("  [%d] %s%n".formatted(i + 1, response.citations().get(i)));
            }
        }

        if (response.relatedQuestions() != null && !response.relatedQuestions().isEmpty()) {
            sb.append("\nRelated:\n");
            for (String q : response.relatedQuestions()) {
                sb.append("  - ").append(q).append("\n");
            }
        }

        if (renderImages && response.images() != null && !response.images().isEmpty()) {
            sb.append("\n");
            for (String imageUrl : response.images()) {
                imageRenderer.renderImageUrl(imageUrl, "Perplexity result image");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String formatSearchResponse(SearchApiResponse response) {
        var sb = new StringBuilder();
        sb.append("Search Results (%d total):\n\n".formatted(response.totalResults()));

        if (response.results() != null) {
            for (int i = 0; i < response.results().size(); i++) {
                SearchResult r = response.results().get(i);
                sb.append("%d. %s%n".formatted(i + 1, r.title() != null ? r.title() : "Untitled"));
                sb.append("   %s%n".formatted(r.url()));
                if (r.snippet() != null && !r.snippet().isBlank()) {
                    sb.append("   %s%n".formatted(r.snippet()));
                }
                sb.append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String formatAgentResponse(AgentResponse response) {
        var sb = new StringBuilder();

        if (response.steps() != null && !response.steps().isEmpty()) {
            sb.append("Research Steps:\n");
            for (int i = 0; i < response.steps().size(); i++) {
                AgentStep step = response.steps().get(i);
                sb.append("  %d. [%s] %s%n".formatted(i + 1, step.toolName(),
                        step.output() != null ? step.output().substring(0, Math.min(200, step.output().length())) : ""));
            }
            sb.append("\n");
        }

        if (response.content() != null) {
            sb.append(response.content());
        }

        if (response.citations() != null && !response.citations().isEmpty()) {
            sb.append("\n\nSources:\n");
            for (int i = 0; i < response.citations().size(); i++) {
                Citation c = response.citations().get(i);
                sb.append("  [%d] %s — %s%n".formatted(i + 1, c.title() != null ? c.title() : "Source", c.url()));
            }
        }

        if (response.usage() != null) {
            sb.append("\n[Tokens: %d prompt + %d completion = %d total]".formatted(
                    response.usage().promptTokens(), response.usage().completionTokens(), response.usage().totalTokens()));
        }

        return sb.toString().stripTrailing();
    }

    private List<String> parseDomains(String domains) {
        if (domains == null || domains.isBlank()) return null;
        return Arrays.stream(domains.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            return "Failed to serialize response: " + e.getMessage();
        }
    }
}
