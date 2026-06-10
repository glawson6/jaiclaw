package io.jaiclaw.pipeline.validation;

import java.util.Collection;
import java.util.Optional;

/**
 * Small Levenshtein edit-distance utility used by the pipeline validator to
 * suggest near-miss names (e.g. {@code "resarch"} → {@code "research"}).
 */
final class Levenshtein {

    private Levenshtein() {}

    /**
     * Compute the Levenshtein edit distance between {@code a} and {@code b}.
     */
    static int distance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ai = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ai == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    /**
     * Return the closest candidate to {@code target} whose edit distance is
     * less than or equal to {@code maxDistance}, or empty if none qualifies.
     */
    static Optional<String> suggest(String target, Collection<String> candidates, int maxDistance) {
        if (target == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            if (candidate == null || candidate.equals(target)) continue;
            int d = distance(target, candidate);
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        return (best != null && bestDist <= maxDistance) ? Optional.of(best) : Optional.empty();
    }
}
