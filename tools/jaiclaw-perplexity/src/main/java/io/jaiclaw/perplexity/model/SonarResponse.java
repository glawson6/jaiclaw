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
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String model;
        private List<Choice> choices;
        private Usage usage;
        private List<String> citations;
        private List<SearchResult> searchResults;
        private List<String> relatedQuestions;
        private List<String> images;

        public Builder id(String id) { this.id = id; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder choices(List<Choice> choices) { this.choices = choices; return this; }
        public Builder usage(Usage usage) { this.usage = usage; return this; }
        public Builder citations(List<String> citations) { this.citations = citations; return this; }
        public Builder searchResults(List<SearchResult> searchResults) { this.searchResults = searchResults; return this; }
        public Builder relatedQuestions(List<String> relatedQuestions) { this.relatedQuestions = relatedQuestions; return this; }
        public Builder images(List<String> images) { this.images = images; return this; }

        public SonarResponse build() {
            return new SonarResponse(
                    id, model, choices, usage, citations, searchResults, relatedQuestions, images);
        }
    }
}
