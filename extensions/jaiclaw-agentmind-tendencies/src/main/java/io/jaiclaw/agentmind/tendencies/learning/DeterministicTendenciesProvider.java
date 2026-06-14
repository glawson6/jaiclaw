package io.jaiclaw.agentmind.tendencies.learning;

import io.jaiclaw.core.model.Tendencies;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Default {@link TendenciesLearningProvider} — zero-LLM-cost rule-based
 * extraction. Uses a constrained trait vocabulary to keep the produced
 * map noise-free; opt into the LLM-driven provider when richer traits
 * are needed.
 *
 * <p>The vocabulary (open question resolved Phase 3, task 3.13):
 * <ul>
 *   <li>{@code prefers_brevity} — true / false / unknown. True if the user's
 *       average message length (excluding very short ack messages) is
 *       below the brevity threshold.</li>
 *   <li>{@code prefers_detail} — inverse signal — true if the average
 *       message length is above the detail threshold.</li>
 *   <li>{@code tech_leaning} — high / medium / low — count of technical
 *       keywords (code, function, error, etc.) per message.</li>
 *   <li>{@code prefers_bullets} — true / false — fraction of messages
 *       containing list markers ({@code -}, {@code *}, numbered).</li>
 *   <li>{@code prefers_examples} — true / false — fraction of messages
 *       referencing example-asking phrasing.</li>
 *   <li>{@code question_rate} — high / medium / low — fraction of
 *       messages ending in a {@code ?}.</li>
 * </ul>
 *
 * <p>The PeerCard markdown is rendered from a template with one line per
 * trait whose value is observed (not "unknown").
 *
 * <p>Empty transcript → no change → empty Optional.
 *
 * <p>Plan §8 task 3.5.
 */
public class DeterministicTendenciesProvider implements TendenciesLearningProvider {

    public static final String TYPE = "deterministic";

    private static final int BREVITY_THRESHOLD = 80;   // avg chars
    private static final int DETAIL_THRESHOLD = 300;
    private static final int MIN_MESSAGES_FOR_INFERENCE = 3;

    private static final Pattern TECH_KEYWORDS = Pattern.compile(
            "(?i)\\b(code|function|class|method|api|error|exception|" +
            "log|stack\\s?trace|build|deploy|kubernetes|docker|" +
            "regex|sql|http|json|yaml|terraform)\\b");

    private static final Pattern BULLET_MARKERS = Pattern.compile(
            "(?m)^\\s*(?:[-*]\\s|\\d+\\.\\s)");

    private static final Pattern EXAMPLE_ASKING = Pattern.compile(
            "(?i)\\b(can you show|give me an example|for example|sample|" +
            "demonstrate|walk\\s?through)\\b");

    @Override
    public String type() { return TYPE; }

    @Override
    public Optional<Tendencies> learn(Tendencies current, List<String> transcript) {
        if (transcript == null || transcript.size() < MIN_MESSAGES_FOR_INFERENCE) {
            return Optional.empty();
        }

        Map<String, String> traits = new LinkedHashMap<>();

        double avgLen = transcript.stream().mapToInt(String::length).average().orElse(0);
        if (avgLen < BREVITY_THRESHOLD) traits.put("prefers_brevity", "true");
        else if (avgLen > DETAIL_THRESHOLD) traits.put("prefers_detail", "true");

        long techHits = transcript.stream()
                .filter(m -> TECH_KEYWORDS.matcher(m).find())
                .count();
        double techRate = (double) techHits / transcript.size();
        if (techRate > 0.5) traits.put("tech_leaning", "high");
        else if (techRate > 0.2) traits.put("tech_leaning", "medium");
        else if (techRate > 0.0) traits.put("tech_leaning", "low");

        long bulletHits = transcript.stream()
                .filter(m -> BULLET_MARKERS.matcher(m).find())
                .count();
        if ((double) bulletHits / transcript.size() > 0.3) {
            traits.put("prefers_bullets", "true");
        }

        long exampleHits = transcript.stream()
                .filter(m -> EXAMPLE_ASKING.matcher(m).find())
                .count();
        if ((double) exampleHits / transcript.size() > 0.2) {
            traits.put("prefers_examples", "true");
        }

        long questionHits = transcript.stream()
                .filter(m -> m.trim().endsWith("?"))
                .count();
        double questionRate = (double) questionHits / transcript.size();
        if (questionRate > 0.5) traits.put("question_rate", "high");
        else if (questionRate > 0.2) traits.put("question_rate", "medium");
        else if (questionRate > 0.0) traits.put("question_rate", "low");

        if (traits.isEmpty()) {
            return Optional.empty();
        }
        if (traits.equals(current.traits())) {
            return Optional.empty(); // no observable change since last pass
        }
        String markdown = renderPeerCard(traits);
        return Optional.of(current.withDialecticResult(markdown, traits));
    }

    static String renderPeerCard(Map<String, String> traits) {
        StringBuilder sb = new StringBuilder("# Observed Tendencies\n");
        traits.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }
}
