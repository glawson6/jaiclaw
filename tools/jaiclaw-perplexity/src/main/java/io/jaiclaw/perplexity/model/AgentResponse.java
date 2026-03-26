package io.jaiclaw.perplexity.model;

import java.util.List;

public record AgentResponse(
        String id,
        String content,
        List<Citation> citations,
        List<AgentStep> steps,
        Usage usage
) {}
