package io.jclaw.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Combines keyword search (BM25-style) with temporal decay for workspace memory.
 * Searches across MEMORY.md, daily logs, and session transcripts.
 *
 * <p>Results are ranked by: relevance_score * temporal_decay_factor
 * where temporal_decay = exp(-age_days / half_life)
 */
public class HybridSearchManager implements MemorySearchManager {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchManager.class);

    private final InMemorySearchManager keywordSearch;
    private final WorkspaceMemoryManager workspaceMemory;
    private final DailyLogAppender dailyLog;
    private final double temporalDecayHalfLife;

    public HybridSearchManager(WorkspaceMemoryManager workspaceMemory,
                                DailyLogAppender dailyLog,
                                double temporalDecayHalfLife) {
        this.keywordSearch = new InMemorySearchManager();
        this.workspaceMemory = workspaceMemory;
        this.dailyLog = dailyLog;
        this.temporalDecayHalfLife = temporalDecayHalfLife;
    }

    @Override
    public List<MemorySearchResult> search(String query, MemorySearchOptions options) {
        refreshIndex();
        List<MemorySearchResult> results = keywordSearch.search(query, options);

        return results.stream()
                .map(r -> applyTemporalDecay(r))
                .filter(r -> r.score() >= options.minScore())
                .sorted(Comparator.comparingDouble(MemorySearchResult::score).reversed())
                .limit(options.maxResults())
                .toList();
    }

    private void refreshIndex() {
        keywordSearch.clear();

        String memory = workspaceMemory.readMemory();
        if (!memory.isEmpty()) {
            keywordSearch.addEntry("MEMORY.md", memory, MemorySource.MEMORY);
        }

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.minusDays(i);
            String logContent = dailyLog.readLog(date);
            if (!logContent.isEmpty()) {
                keywordSearch.addEntry("memory/" + date + ".md", logContent, MemorySource.MEMORY);
            }
        }
    }

    private MemorySearchResult applyTemporalDecay(MemorySearchResult result) {
        double ageDays = estimateAgeDays(result.path());
        double decay = Math.exp(-ageDays / temporalDecayHalfLife);
        double adjustedScore = result.score() * decay;
        return new MemorySearchResult(
                result.path(), result.startLine(), result.endLine(),
                adjustedScore, result.snippet(), result.source());
    }

    private double estimateAgeDays(String path) {
        if (path.equals("MEMORY.md")) return 0;
        try {
            String datePart = path.replace("memory/", "").replace(".md", "");
            LocalDate date = LocalDate.parse(datePart);
            return ChronoUnit.DAYS.between(date, LocalDate.now());
        } catch (Exception e) {
            return 0;
        }
    }
}
