package io.jaiclaw.copilot;

import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

/**
 * Per-call and global-default options for {@link CopilotChatModel}.
 *
 * <p>Implements Spring AI's {@link ChatOptions} so JaiClaw and other
 * downstream code can pass a Copilot options instance anywhere a generic
 * {@code ChatOptions} is expected — the model, temperature, and max-tokens
 * fields all round-trip through the standard interface.
 *
 * <p><b>Resolution precedence</b> at call time:
 * <ol>
 *   <li>Per-call override: {@code new Prompt(text, copilotOptions)} on the
 *       inbound {@code Prompt}.</li>
 *   <li>Global default: the {@code CopilotChatOptions} bean bound from
 *       {@code jaiclaw.copilot.chat.options.*} and passed to the
 *       {@code CopilotChatModel} constructor.</li>
 *   <li>SDK default: when both are null, the model name is passed as
 *       {@code null} to {@code SessionConfig.setModel(...)} and Copilot's
 *       server picks.</li>
 * </ol>
 *
 * <p>Not every field on the standard {@link ChatOptions} contract maps to a
 * Copilot SDK knob. Unsupported fields return {@code null} from their
 * getters (and their setters no-op) — this is deliberate, and matches how
 * Spring AI's other provider integrations handle provider-specific gaps.
 */
public class CopilotChatOptions implements ChatOptions {

    private String model;
    private Double temperature;
    private Integer maxTokens;

    // Fields required by ChatOptions but not currently mapped to the Copilot
    // SDK. Setters accept and store them so a caller building a generic
    // ChatOptions round-trips cleanly; getters return them so debug tooling
    // works. The values are ignored at session build time.
    private Double topP;
    private Integer topK;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private List<String> stopSequences;

    public CopilotChatOptions() {
    }

    /**
     * Copy constructor — deep-copies scalar fields and passes the
     * {@code stopSequences} list reference through (the list itself is
     * treated as immutable in this codebase).
     */
    public CopilotChatOptions(CopilotChatOptions other) {
        if (other != null) {
            this.model = other.model;
            this.temperature = other.temperature;
            this.maxTokens = other.maxTokens;
            this.topP = other.topP;
            this.topK = other.topK;
            this.frequencyPenalty = other.frequencyPenalty;
            this.presencePenalty = other.presencePenalty;
            this.stopSequences = other.stopSequences;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setModel(CopilotModel model) {
        this.model = model == null ? null : model.toApiName();
    }

    @Override
    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    @Override
    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    @Override
    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    @Override
    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    @Override
    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    @Override
    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    /**
     * Returns a mutable copy of these options. Spring AI's default merge path
     * expects a functioning {@link ChatOptions.Builder}; we return our own
     * builder pre-populated with the current field values.
     */
    @Override
    public Builder mutate() {
        Builder b = new Builder();
        b.opts.model = this.model;
        b.opts.temperature = this.temperature;
        b.opts.maxTokens = this.maxTokens;
        b.opts.topP = this.topP;
        b.opts.topK = this.topK;
        b.opts.frequencyPenalty = this.frequencyPenalty;
        b.opts.presencePenalty = this.presencePenalty;
        b.opts.stopSequences = this.stopSequences;
        return b;
    }

    /**
     * Copy-of factory returning a fresh {@link CopilotChatOptions} with
     * fields resolved via the standard precedence rule: fields on
     * {@code perCall} override fields on {@code defaults}. Nulls on
     * {@code perCall} fall through to {@code defaults}; nulls on both stay
     * null (SDK-default).
     */
    public static CopilotChatOptions merge(CopilotChatOptions perCall, CopilotChatOptions defaults) {
        CopilotChatOptions merged = new CopilotChatOptions();
        merged.model = firstNonNull(perCall == null ? null : perCall.model, defaults == null ? null : defaults.model);
        merged.temperature = firstNonNull(perCall == null ? null : perCall.temperature, defaults == null ? null : defaults.temperature);
        merged.maxTokens = firstNonNull(perCall == null ? null : perCall.maxTokens, defaults == null ? null : defaults.maxTokens);
        merged.topP = firstNonNull(perCall == null ? null : perCall.topP, defaults == null ? null : defaults.topP);
        merged.topK = firstNonNull(perCall == null ? null : perCall.topK, defaults == null ? null : defaults.topK);
        merged.frequencyPenalty = firstNonNull(perCall == null ? null : perCall.frequencyPenalty, defaults == null ? null : defaults.frequencyPenalty);
        merged.presencePenalty = firstNonNull(perCall == null ? null : perCall.presencePenalty, defaults == null ? null : defaults.presencePenalty);
        merged.stopSequences = firstNonNull(perCall == null ? null : perCall.stopSequences, defaults == null ? null : defaults.stopSequences);
        return merged;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    /**
     * Fluent builder — the mutable target is a private {@link CopilotChatOptions}
     * that {@link #build()} returns directly (no defensive copy — builders are
     * single-use).
     *
     * <p>Self-typed as {@code Builder<Builder>} to satisfy Spring AI's
     * {@code ChatOptions.Builder<B extends ChatOptions.Builder<B>>} contract
     * so every fluent setter returns a {@code CopilotChatOptions.Builder}
     * rather than the erased root type.
     */
    public static class Builder implements ChatOptions.Builder<Builder> {

        private final CopilotChatOptions opts = new CopilotChatOptions();

        @Override
        public Builder model(String model) {
            opts.model = model;
            return this;
        }

        public Builder model(CopilotModel model) {
            opts.model = model == null ? null : model.toApiName();
            return this;
        }

        @Override
        public Builder temperature(Double temperature) {
            opts.temperature = temperature;
            return this;
        }

        @Override
        public Builder maxTokens(Integer maxTokens) {
            opts.maxTokens = maxTokens;
            return this;
        }

        @Override
        public Builder topP(Double topP) {
            opts.topP = topP;
            return this;
        }

        @Override
        public Builder topK(Integer topK) {
            opts.topK = topK;
            return this;
        }

        @Override
        public Builder frequencyPenalty(Double frequencyPenalty) {
            opts.frequencyPenalty = frequencyPenalty;
            return this;
        }

        @Override
        public Builder presencePenalty(Double presencePenalty) {
            opts.presencePenalty = presencePenalty;
            return this;
        }

        @Override
        public Builder stopSequences(List<String> stopSequences) {
            opts.stopSequences = stopSequences;
            return this;
        }

        @Override
        public Builder combineWith(ChatOptions.Builder<?> other) {
            // Merge: fields on `other` fill in nulls on this builder.
            if (other == null) {
                return this;
            }
            ChatOptions merged = other.build();
            if (opts.model == null) opts.model = merged.getModel();
            if (opts.temperature == null) opts.temperature = merged.getTemperature();
            if (opts.maxTokens == null) opts.maxTokens = merged.getMaxTokens();
            if (opts.topP == null) opts.topP = merged.getTopP();
            if (opts.topK == null) opts.topK = merged.getTopK();
            if (opts.frequencyPenalty == null) opts.frequencyPenalty = merged.getFrequencyPenalty();
            if (opts.presencePenalty == null) opts.presencePenalty = merged.getPresencePenalty();
            if (opts.stopSequences == null) opts.stopSequences = merged.getStopSequences();
            return this;
        }

        @Override
        public Builder clone() {
            Builder copy = new Builder();
            copy.opts.model = this.opts.model;
            copy.opts.temperature = this.opts.temperature;
            copy.opts.maxTokens = this.opts.maxTokens;
            copy.opts.topP = this.opts.topP;
            copy.opts.topK = this.opts.topK;
            copy.opts.frequencyPenalty = this.opts.frequencyPenalty;
            copy.opts.presencePenalty = this.opts.presencePenalty;
            copy.opts.stopSequences = this.opts.stopSequences;
            return copy;
        }

        @Override
        public CopilotChatOptions build() {
            return opts;
        }
    }
}
