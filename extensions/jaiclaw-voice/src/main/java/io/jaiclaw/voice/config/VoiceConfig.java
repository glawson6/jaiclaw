package io.jaiclaw.voice.config;

/**
 * Configuration for the voice service.
 */
public record VoiceConfig(
        String ttsProvider,
        String ttsAutoMode,
        String defaultVoice,
        String sttProvider,
        String openaiApiKey,
        String openaiTtsModel,
        String openaiSttModel,
        String elevenLabsApiKey,
        String elevenLabsVoiceId,
        String elevenLabsModelId
) {
    public VoiceConfig {
        if (ttsProvider == null) ttsProvider = "openai";
        if (ttsAutoMode == null) ttsAutoMode = "off";
        if (defaultVoice == null) defaultVoice = "alloy";
        if (sttProvider == null) sttProvider = "openai";
        if (openaiTtsModel == null) openaiTtsModel = "tts-1";
        if (openaiSttModel == null) openaiSttModel = "whisper-1";
    }

    public boolean isTtsAuto() {
        return !"off".equalsIgnoreCase(ttsAutoMode);
    }

    public static final VoiceConfig DEFAULT = new VoiceConfig(
            "openai", "off", "alloy", "openai",
            null, "tts-1", "whisper-1",
            null, null, null);

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String ttsProvider;
        private String ttsAutoMode;
        private String defaultVoice;
        private String sttProvider;
        private String openaiApiKey;
        private String openaiTtsModel;
        private String openaiSttModel;
        private String elevenLabsApiKey;
        private String elevenLabsVoiceId;
        private String elevenLabsModelId;

        public Builder ttsProvider(String ttsProvider) { this.ttsProvider = ttsProvider; return this; }
        public Builder ttsAutoMode(String ttsAutoMode) { this.ttsAutoMode = ttsAutoMode; return this; }
        public Builder defaultVoice(String defaultVoice) { this.defaultVoice = defaultVoice; return this; }
        public Builder sttProvider(String sttProvider) { this.sttProvider = sttProvider; return this; }
        public Builder openaiApiKey(String openaiApiKey) { this.openaiApiKey = openaiApiKey; return this; }
        public Builder openaiTtsModel(String openaiTtsModel) { this.openaiTtsModel = openaiTtsModel; return this; }
        public Builder openaiSttModel(String openaiSttModel) { this.openaiSttModel = openaiSttModel; return this; }
        public Builder elevenLabsApiKey(String elevenLabsApiKey) { this.elevenLabsApiKey = elevenLabsApiKey; return this; }
        public Builder elevenLabsVoiceId(String elevenLabsVoiceId) { this.elevenLabsVoiceId = elevenLabsVoiceId; return this; }
        public Builder elevenLabsModelId(String elevenLabsModelId) { this.elevenLabsModelId = elevenLabsModelId; return this; }

        public VoiceConfig build() {
            return new VoiceConfig(
                    ttsProvider, ttsAutoMode, defaultVoice, sttProvider, openaiApiKey, openaiTtsModel, openaiSttModel, elevenLabsApiKey, elevenLabsVoiceId, elevenLabsModelId);
        }
    }
}
