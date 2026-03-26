package io.jaiclaw.perplexity.model;

import java.util.List;

public record SonarResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage,
        List<String> citations,
        List<SearchResult> searchResults,
        List<String> relatedQuestions,
        List<String> images
) {}
