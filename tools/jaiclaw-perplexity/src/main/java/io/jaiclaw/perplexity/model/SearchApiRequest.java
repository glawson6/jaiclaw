package io.jaiclaw.perplexity.model;

import java.util.List;

public record SearchApiRequest(
        String query,
        Integer numResults,
        String recencyFilter,
        List<String> domainFilter
) {}
