package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import io.jclaw.shell.commands.setup.validation.LlmConnectivityTester;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.flow.SingleItemSelectorSpec;

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

        // Step 2: API key (skip for Ollama)
        if (!"ollama".equals(provider)) {
            ComponentFlow keyFlow = flowBuilder.clone().reset()
                    .withStringInput("api-key")
                        .name("Enter your " + providerDisplayName(provider) + " API key:")
                        .maskCharacter('*')
                        .and()
                    .build();

            ComponentFlow.ComponentFlowResult keyResult = keyFlow.run();
            String apiKey = WizardStep.getOrNull(keyResult.getContext(), "api-key", String.class);
            if (apiKey == null || apiKey.isBlank()) {
                System.out.println("  API key is required for " + providerDisplayName(provider));
                return false;
            }
            result.setLlmApiKey(apiKey);
        } else {
            // Ollama base URL
            ComponentFlow urlFlow = flowBuilder.clone().reset()
                    .withStringInput("ollama-url")
                        .name("Ollama base URL:")
                        .defaultValue("http://localhost:11434")
                        .and()
                    .build();

            ComponentFlow.ComponentFlowResult urlResult = urlFlow.run();
            String url = WizardStep.getOrNull(urlResult.getContext(), "ollama-url", String.class);
            if (url != null && !url.isBlank()) {
                result.setOllamaBaseUrl(url);
            }
        }

        // Step 3: Choose model
        List<String> models = MODELS.getOrDefault(provider, List.of());
        SingleItemSelectorSpec modelSelectorSpec = flowBuilder.clone().reset()
                .withSingleItemSelector("model")
                    .name("Choose a model:");
        for (String model : models) {
            modelSelectorSpec.selectItem(model, model);
        }
        ComponentFlow modelFlow = modelSelectorSpec.and().build();

        ComponentFlow.ComponentFlowResult modelResult = modelFlow.run();
        String model = WizardStep.getOrNull(modelResult.getContext(), "model", String.class);
        if (model == null) return false;
        result.setLlmModel(model);

        // Step 4: Test connectivity
        ComponentFlow testFlow = flowBuilder.clone().reset()
                .withConfirmationInput("test-connection")
                    .name("Test connection to " + providerDisplayName(provider) + "?")
                    .defaultValue(true)
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult testResult = testFlow.run();
        Boolean shouldTest = WizardStep.getOrNull(testResult.getContext(), "test-connection", Boolean.class);

        if (Boolean.TRUE.equals(shouldTest)) {
            System.out.print("  Testing connection... ");
            LlmConnectivityTester.TestResult testOutcome = llmTester.test(
                    provider, result.llmApiKey(), model, result.ollamaBaseUrl());
            if (testOutcome.success()) {
                System.out.println("OK");
            } else {
                System.out.println("FAILED: " + testOutcome.message());
                System.out.println("  You can continue setup and fix this later.");
            }
        }

        return true;
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
