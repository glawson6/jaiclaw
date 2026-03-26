package io.jaiclaw.perplexity.model;

import java.util.List;

public record AgentRequest(
        String preset,
        String model,
        List<Message> messages,
        Integer maxTokens,
        List<String> tools
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String preset = "pro-search";
        private String model;
        private List<Message> messages = List.of();
        private Integer maxTokens;
        private List<String> tools;

        public Builder preset(String preset) { this.preset = preset; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder messages(List<Message> messages) { this.messages = messages; return this; }
        public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder tools(List<String> tools) { this.tools = tools; return this; }

        public AgentRequest build() {
            return new AgentRequest(preset, model, messages, maxTokens, tools);
        }
    }
}
