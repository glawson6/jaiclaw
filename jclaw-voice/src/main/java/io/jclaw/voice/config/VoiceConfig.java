package io.jclaw.voice.config;

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
}
