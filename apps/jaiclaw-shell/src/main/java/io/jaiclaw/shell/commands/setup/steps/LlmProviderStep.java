package io.jaiclaw.shell.commands.setup.steps;

import io.jaiclaw.shell.commands.setup.OnboardResult;
import io.jaiclaw.shell.commands.setup.WizardStep;
import io.jaiclaw.shell.commands.setup.validation.LlmConnectivityTester;
import org.springframework.shell.component.flow.ComponentFlow;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

public final class LlmProviderStep implements WizardStep {

    private static final Map<String, List<String>> MODELS = Map.of(
            "openai", List.of("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o3-mini"),
            "anthropic", List.of("claude-sonnet-4-6", "claude-opus-4-6", "claude-haiku-4-5-20251001"),
            "ollama", List.of("llama3", "llama3:70b", "mistral", "codellama", "gemma2")
    );

    private final ComponentFlow.Builder flowBuilder;
    private final LlmConnectivityTester llmTester;

    public LlmProviderStep(ComponentFlow.Builder flowBuilder, LlmConnectivityTester llmTester) {
        this.flowBuilder = flowBuilder;
        this.llmTester = llmTester;
    }

    @Override
    public String name() {
        return "LLM Provider";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // Step 1: Choose provider
        ComponentFlow providerFlow = flowBuilder.clone().reset()
                .withSingleItemSelector("provider")
                    .name("Choose your LLM provider:")
                    .selectItem("openai", "OpenAI (GPT-4o, GPT-4.1)")
                    .selectItem("anthropic", "Anthropic (Claude Sonnet, Opus)")
                    .selectItem("ollama", "Ollama (local, no API key needed)")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult providerResult = providerFlow.run();
        String provider = WizardStep.getOrNull(providerResult.getContext(), "provider", String.class);
        if (provider == null) return false;
        result.setLlmProvider(provider);

        // Step 2: API key or Ollama URL
        if (!"ollama".equals(provider)) {
            String apiKey = readLine("  Enter your " + providerDisplayName(provider) + " API key: ");
            if (apiKey == null || apiKey.isBlank()) {
                System.out.println("  API key is required for " + providerDisplayName(provider));
                return false;
            }
            result.setLlmApiKey(apiKey);

            // Validate key before proceeding to model selection
            System.out.print("  Validating API key... ");
            LlmConnectivityTester.TestResult validation = llmTester.test(
                    provider, apiKey, MODELS.get(provider).getFirst(), null);
            if (validation.success()) {
                System.out.println("OK");
            } else {
                System.out.println("FAILED: " + validation.message());
                System.out.println("  You can continue setup and fix the key later.");
            }
        } else {
            String url = readLine("  Ollama base URL [http://localhost:11434]: ");
            if (url == null || url.isBlank()) {
                url = "http://localhost:11434";
            }
            result.setOllamaBaseUrl(url);
        }

        // Step 3: Choose model
        List<String> models = MODELS.getOrDefault(provider, List.of());
        var modelSpec = flowBuilder.clone().reset()
                .withSingleItemSelector("model")
                    .name("Choose a model:");
        for (String m : models) {
            modelSpec = modelSpec.selectItem(m, m);
        }
        ComponentFlow modelFlow = modelSpec.and().build();

        ComponentFlow.ComponentFlowResult modelResult = modelFlow.run();
        String model = WizardStep.getOrNull(modelResult.getContext(), "model", String.class);
        if (model == null) return false;
        result.setLlmModel(model);

        return true;
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private String providerDisplayName(String provider) {
        return switch (provider) {
            case "openai" -> "OpenAI";
            case "anthropic" -> "Anthropic";
            case "ollama" -> "Ollama";
            default -> provider;
        };
    }
}
