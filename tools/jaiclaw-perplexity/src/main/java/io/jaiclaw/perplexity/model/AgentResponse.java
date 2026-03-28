package io.jaiclaw.perplexity.model;

import java.util.List;

public record AgentResponse(
        String id,
        String content,
        List<Citation> citations,
        List<AgentStep> steps,
        Usage usage
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String content;
        private List<Citation> citations;
        private List<AgentStep> steps;
        private Usage usage;

        public Builder id(String id) { this.id = id; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder citations(List<Citation> citations) { this.citations = citations; return this; }
        public Builder steps(List<AgentStep> steps) { this.steps = steps; return this; }
        public Builder usage(Usage usage) { this.usage = usage; return this; }

        public AgentResponse build() {
            return new AgentResponse(id, content, citations, steps, usage);
        }
    }
}
