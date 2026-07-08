package io.jaiclaw.config;

import java.util.Map;

/**
 * T1-4: catalog of known HIPAA-BAA-eligibility defaults per LLM provider.
 *
 * <p>Ships with the following assumptions — all reflect the state at
 * 0.9.4-cut. Operators can override any of these by setting
 * {@code jaiclaw.models.providers.<name>.baa-eligible} in application.yml.
 *
 * <p><strong>BAA-eligible by default</strong> (cloud routes that offer a
 * Business Associate Agreement under standard contract):
 * <ul>
 *   <li>{@code bedrock} — Anthropic + others via AWS Bedrock (BAA with AWS)</li>
 *   <li>{@code azure-openai} — OpenAI models via Azure with contract rider</li>
 *   <li>{@code vertex-ai} — Google models via Vertex AI (BAA with GCP)</li>
 *   <li>{@code ollama} — self-hosted; deployer controls the data plane</li>
 * </ul>
 *
 * <p><strong>NOT BAA-eligible by default</strong> (direct public API routes
 * that do not offer a standard BAA):
 * <ul>
 *   <li>{@code anthropic} — direct {@code api.anthropic.com}</li>
 *   <li>{@code openai} — direct {@code api.openai.com}</li>
 *   <li>{@code gemini}, {@code google-genai} — direct public Gemini API</li>
 *   <li>{@code minimax}, {@code deepseek}, {@code mistral}, {@code oci-genai} —
 *       direct public APIs</li>
 * </ul>
 *
 * <p>A future BAA offering from any of the "not by default" providers can be
 * accommodated by setting the config property; no code change required.
 */
public final class BaaEligibleProviders {

    private static final Map<String, Boolean> DEFAULTS = Map.ofEntries(
            // BAA-eligible routes
            Map.entry("bedrock", true),
            Map.entry("azure-openai", true),
            Map.entry("vertex-ai", true),
            Map.entry("ollama", true),
            // Direct public routes
            Map.entry("anthropic", false),
            Map.entry("openai", false),
            Map.entry("gemini", false),
            Map.entry("google-genai", false),
            Map.entry("minimax", false),
            Map.entry("deepseek", false),
            Map.entry("mistral", false),
            Map.entry("mistral-ai", false),
            Map.entry("oci-genai", false)
    );

    private BaaEligibleProviders() {}

    /**
     * Resolve BAA-eligibility for a provider. Precedence: (1) explicit config
     * value if set, (2) known default from the table above, (3) false for
     * unknown provider names (safer default — operator must explicitly say
     * "yes" for a custom provider).
     */
    public static boolean resolve(String providerName, ModelsProperties.ModelProviderConfig config) {
        if (config != null && config.baaEligible() != null) {
            return config.baaEligible();
        }
        if (providerName == null) return false;
        Boolean known = DEFAULTS.get(providerName.trim().toLowerCase());
        return known != null && known;
    }

    /** For test / doc introspection: the full defaults map (unmodifiable). */
    public static Map<String, Boolean> defaults() {
        return DEFAULTS;
    }
}
