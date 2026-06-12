package io.jaiclaw.kanban.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Point-in-time view of a board: ordered cards per column plus computed
 * WIP counts. Primary payload for the dashboard REST API, the SSE
 * snapshot-on-connect, and the ASCII renderer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoardSnapshot(
        String boardId,
        String boardName,
        Instant capturedAt,
        List<ColumnSnapshot> columns,
        int totalCards
) {
    public BoardSnapshot {
        if (capturedAt == null) capturedAt = Instant.now();
        if (columns == null) columns = List.of();
        else columns = List.copyOf(columns);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ColumnSnapshot(
            String state,
            String name,
            Integer wipLimit,
            boolean terminal,
            List<CardView> cards
    ) {
        public ColumnSnapshot {
            if (cards == null) cards = List.of();
            else cards = List.copyOf(cards);
        }

        public int cardCount() {
            return cards.size();
        }

        public boolean overWip() {
            return wipLimit != null && cards.size() > wipLimit;
        }
    }

    public Map<String, Integer> cardCountByState() {
        return columns.stream().collect(
                java.util.stream.Collectors.toMap(
                        ColumnSnapshot::state, ColumnSnapshot::cardCount));
    }
}
