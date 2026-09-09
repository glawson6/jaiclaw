package io.jaiclaw.tools.search;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A small lexical index over tool metadata, used by {@code tool_search} to surface
 * deferred tools on demand.
 *
 * <p>Deliberately lexical, not vector-based: {@code jaiclaw-tools} is a dependency
 * of nearly every module, and an embedding model or vector store there would be
 * felt by every adopter. A richer ranker can be supplied later as an SPI
 * implementation without changing the tool surface. Recorded in the plan's
 * decision log.
 *
 * <p>Scoring is term overlap with field weighting — an exact name match dominates,
 * then name substrings, then keywords, section and description. Enough to answer
 * "which tool reads a file", which is what this exists for.
 *
 * <p>Instances are immutable; the registry rebuilds one on register/unregister.
 *
 * <p>Phase 3 of the 1.2.0 plan.
 */
@Experimental
public final class ToolSearchIndex {

    /** Weight for an exact, whole-name match. Dominates every other signal. */
    private static final int W_NAME_EXACT = 100;
    private static final int W_NAME_TERM = 25;
    private static final int W_KEYWORD = 12;
    private static final int W_SECTION = 8;
    private static final int W_DESCRIPTION = 4;

    /**
     * Words carrying no discriminating signal in a tool query. Kept tiny and
     * literal — an aggressive stopword list would drop terms like "read" or
     * "list" that are exactly what distinguishes one tool from another.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "the", "to", "of", "for", "in", "on", "with",
            "i", "me", "my", "want", "need", "please", "can", "you", "how", "do");

    private final List<Entry> entries;

    private ToolSearchIndex(List<Entry> entries) {
        this.entries = entries;
    }

    /** Builds an index over the given definitions. */
    public static ToolSearchIndex of(List<ToolDefinition> definitions) {
        List<Entry> built = new ArrayList<>(definitions.size());
        for (ToolDefinition d : definitions) {
            if (d == null) continue;
            built.add(new Entry(d,
                    tokenize(d.name()),
                    lowerSet(d.keywords()),
                    lower(d.section()),
                    tokenize(d.description())));
        }
        return new ToolSearchIndex(List.copyOf(built));
    }

    /** An empty index; every search returns nothing. */
    public static ToolSearchIndex empty() {
        return new ToolSearchIndex(List.of());
    }

    public int size() {
        return entries.size();
    }

    /**
     * Ranks tools against a free-text query.
     *
     * @param query free text, e.g. "read a file" or "kubernetes pods"
     * @param limit maximum results; values {@code <= 0} yield an empty list
     * @return matching definitions, best first; never null
     */
    public List<ToolDefinition> search(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) return List.of();
        Set<String> terms = queryTerms(query);
        if (terms.isEmpty()) return List.of();

        String normalizedQuery = lower(query).trim();

        List<Scored> scored = new ArrayList<>();
        for (Entry e : entries) {
            int score = score(e, terms, normalizedQuery);
            if (score > 0) scored.add(new Scored(e.definition(), score));
        }
        scored.sort(Comparator
                .comparingInt(Scored::score).reversed()
                // Stable tie-break so results do not reshuffle between identical
                // queries — a model that sees a different order each turn cannot
                // build a reliable habit.
                .thenComparing(s -> s.definition().name()));

        List<ToolDefinition> out = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < scored.size() && out.size() < limit; i++) {
            out.add(scored.get(i).definition());
        }
        return out;
    }

    private int score(Entry e, Set<String> terms, String normalizedQuery) {
        int score = 0;

        String name = lower(e.definition().name());
        if (name.equals(normalizedQuery)) score += W_NAME_EXACT;

        for (String term : terms) {
            if (e.nameTokens().contains(term)) {
                score += W_NAME_TERM;
            } else if (name.contains(term)) {
                // Partial name hit ("file" in "file_read") still counts, less.
                score += W_NAME_TERM / 2;
            }
            if (e.keywords().contains(term)) score += W_KEYWORD;
            if (term.equals(e.section())) score += W_SECTION;
            if (e.descriptionTokens().contains(term)) score += W_DESCRIPTION;
        }
        return score;
    }

    private static Set<String> queryTerms(String query) {
        Set<String> terms = new LinkedHashSet<>(tokenize(query));
        terms.removeAll(STOPWORDS);
        return terms;
    }

    /** Splits on any non-alphanumeric run, lowercases, drops 1-character noise. */
    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> tokens = new HashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() > 1) tokens.add(raw);
        }
        return tokens;
    }

    private static Set<String> lowerSet(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        Set<String> out = new HashSet<>(values.size());
        for (String v : values) {
            if (v != null && !v.isBlank()) out.add(lower(v));
        }
        return out;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private record Entry(ToolDefinition definition,
                         Set<String> nameTokens,
                         Set<String> keywords,
                         String section,
                         Set<String> descriptionTokens) {}

    private record Scored(ToolDefinition definition, int score) {}
}
