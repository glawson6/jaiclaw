package io.jaiclaw.shell.commands.setup.steps;

import io.jaiclaw.config.ModelsProperties;
import io.jaiclaw.config.ModelsProperties.ModelProviderConfig;
import io.jaiclaw.shell.commands.setup.OnboardResult;
import io.jaiclaw.shell.commands.setup.WizardStep;
import io.jaiclaw.shell.commands.setup.validation.LlmConnectivityTester;
import org.springframework.shell.component.flow.ComponentFlow;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

public final class LlmProviderStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;
    private final LlmConnectivityTester llmTester;
    private final ModelsProperties modelsProperties;

    public LlmProviderStep(ComponentFlow.Builder flowBuilder, LlmConnectivityTester llmTester,
                           ModelsProperties modelsProperties) {
        this.flowBuilder = flowBuilder;
        this.llmTester = llmTester;
        this.modelsProperties = modelsProperties;
    }

    @Override
    public String name() {
        return "LLM Provider";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // Step 1: Choose provider
        Map<String, ModelProviderConfig> providers = modelsProperties.providers();
        var providerSpec = flowBuilder.clone().reset()
                .withSingleItemSelector("provider")
                    .name("Choose your LLM provider:");

        for (Map.Entry<String, ModelProviderConfig> entry : providers.entrySet()) {
            String key = entry.getKey();
            String displayName = providerDisplayName(key);
            List<String> models = entry.getValue().wizardModels();
            String summary = models.isEmpty() ? displayName : displayName + " (" + String.join(", ", models.subList(0, Math.min(2, models.size()))) + ")";
            providerSpec = providerSpec.selectItem(key, summary);
        }

        ComponentFlow providerFlow = providerSpec.and().build();

        ComponentFlow.ComponentFlowResult providerResult = providerFlow.run();
        String provider = WizardStep.getOrNull(providerResult.getContext(), "provider", String.class);
        if (provider == null) return false;
        result.setLlmProvider(provider);

        // Step 2: API key, Ollama URL, or AWS region
        List<String> wizardModels = getWizardModels(provider);
        if ("ollama".equals(provider)) {
            String url = readLine("  Ollama base URL [http://localhost:11434]: ");
            if (url == null || url.isBlank()) {
                url = "http://localhost:11434";
            }
            result.setOllamaBaseUrl(url);
        } else if ("bedrock".equals(provider)) {
            String region = readLine("  AWS region [us-east-1]: ");
            if (region == null || region.isBlank()) {
                region = "us-east-1";
            }
            result.setAwsRegion(region);
            System.out.println("  AWS credentials will be resolved from: env vars, ~/.aws/credentials, or IAM roles");
        } else {
            String displayName = providerDisplayName(provider);
            String apiKey = readLine("  Enter your " + displayName + " API key: ");
            if (apiKey == null || apiKey.isBlank()) {
                System.out.println("  API key is required for " + displayName);
                return false;
            }
            result.setLlmApiKey(apiKey);

            // Validate key before proceeding to model selection
            if (!wizardModels.isEmpty()) {
                System.out.print("  Validating API key... ");
                LlmConnectivityTester.TestResult validation = llmTester.test(
                        provider, apiKey, wizardModels.getFirst(), null);
                if (validation.success()) {
                    System.out.println("OK");
                } else {
                    System.out.println("FAILED: " + validation.message());
                    System.out.println("  You can continue setup and fix the key later.");
                }
            }
        }

        // Step 3: Choose model
        var modelSpec = flowBuilder.clone().reset()
                .withSingleItemSelector("model")
                    .name("Choose a model:");
        for (String m : wizardModels) {
            modelSpec = modelSpec.selectItem(m, m);
        }
        ComponentFlow modelFlow = modelSpec.and().build();

        ComponentFlow.ComponentFlowResult modelResult = modelFlow.run();
        String model = WizardStep.getOrNull(modelResult.getContext(), "model", String.class);
        if (model == null) return false;
        result.setLlmModel(model);

        return true;
    }

    private List<String> getWizardModels(String provider) {
        ModelProviderConfig config = modelsProperties.providers().get(provider);
        if (config == null) {
            return List.of();
        }
        return config.wizardModels();
    }

    private String providerDisplayName(String provider) {
        ModelProviderConfig config = modelsProperties.providers().get(provider);
        if (config != null && config.displayName() != null) {
            return config.displayName();
        }
        // Capitalize first letter as fallback
        return provider.substring(0, 1).toUpperCase() + provider.substring(1);
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
}
