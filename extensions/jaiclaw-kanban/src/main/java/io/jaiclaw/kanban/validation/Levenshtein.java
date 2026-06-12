package io.jaiclaw.kanban.validation;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Tiny Levenshtein-distance utility for "did you mean?" suggestions in
 * {@link BoardValidator}. Mirrors {@code io.jaiclaw.pipeline.validation.Levenshtein}.
 */
final class Levenshtein {

    private Levenshtein() {}

    static Optional<String> suggest(String input, Collection<String> candidates, int maxDistance) {
        if (input == null || candidates == null || candidates.isEmpty()) return Optional.empty();
        return candidates.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> new Scored(c, distance(input, c)))
                .filter(s -> s.distance() <= maxDistance)
                .min(Comparator.comparingInt(Scored::distance))
                .map(Scored::candidate);
    }

    static int distance(String a, String b) {
        int la = a.length(), lb = b.length();
        if (la == 0) return lb;
        if (lb == 0) return la;
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[la][lb];
    }

    private record Scored(String candidate, int distance) {}
}
