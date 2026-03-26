package io.jaiclaw.perplexity.model;

import java.util.List;

public record SonarRequest(
        String model,
        List<Message> messages,
        Double temperature,
        Integer maxTokens,
        boolean stream,
        List<String> searchDomainFilter,
        String searchRecencyFilter,
        boolean returnImages,
        boolean returnRelatedQuestions
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model = "sonar-pro";
        private List<Message> messages = List.of();
        private Double temperature;
        private Integer maxTokens;
        private boolean stream;
        private List<String> searchDomainFilter;
        private String searchRecencyFilter;
        private boolean returnImages;
        private boolean returnRelatedQuestions;

        public Builder model(String model) { this.model = model; return this; }
        public Builder messages(List<Message> messages) { this.messages = messages; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder stream(boolean stream) { this.stream = stream; return this; }
        public Builder searchDomainFilter(List<String> domains) { this.searchDomainFilter = domains; return this; }
        public Builder searchRecencyFilter(String recency) { this.searchRecencyFilter = recency; return this; }
        public Builder returnImages(boolean returnImages) { this.returnImages = returnImages; return this; }
        public Builder returnRelatedQuestions(boolean returnRelated) { this.returnRelatedQuestions = returnRelated; return this; }

        public SonarRequest build() {
            return new SonarRequest(model, messages, temperature, maxTokens, stream,
                    searchDomainFilter, searchRecencyFilter, returnImages, returnRelatedQuestions);
        }
    }
}
