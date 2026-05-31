package io.jaiclaw.config;

/**
 * Configuration properties for voice (TTS/STT) providers, bound from {@code jaiclaw.voice.*}.
 *
 * @param ttsProvider       default TTS provider ID (e.g. "openai", "elevenlabs")
 * @param sttProvider       default STT provider ID (e.g. "openai", "deepgram")
 * @param defaultVoice      default voice name or ID
 * @param ttsAutoMode       auto TTS mode ("off", "always", "directive")
 * @param openaiApiKey      OpenAI API key for TTS/STT
 * @param openaiTtsModel    OpenAI TTS model (e.g. "tts-1")
 * @param openaiSttModel    OpenAI STT model (e.g. "whisper-1")
 * @param elevenLabsApiKey  ElevenLabs API key
 * @param elevenLabsVoiceId ElevenLabs default voice ID
 * @param elevenLabsModelId ElevenLabs model ID (e.g. "eleven_monolingual_v1")
 * @param deepgramApiKey    Deepgram API key
 * @param deepgramModel     Deepgram model (e.g. "nova-2")
 */
public record VoiceProperties(
        String ttsProvider,
        String sttProvider,
        String defaultVoice,
        String ttsAutoMode,
        String openaiApiKey,
        String openaiTtsModel,
        String openaiSttModel,
        String elevenLabsApiKey,
        String elevenLabsVoiceId,
        String elevenLabsModelId,
        String deepgramApiKey,
        String deepgramModel
) {
    public VoiceProperties {
        if (ttsProvider == null) ttsProvider = "openai";
        if (sttProvider == null) sttProvider = "openai";
        if (defaultVoice == null) defaultVoice = "alloy";
        if (ttsAutoMode == null) ttsAutoMode = "off";
        if (openaiTtsModel == null) openaiTtsModel = "tts-1";
        if (openaiSttModel == null) openaiSttModel = "whisper-1";
        if (deepgramModel == null) deepgramModel = "nova-2";
    }

    public static final VoiceProperties DEFAULT = new VoiceProperties(
            "openai", "openai", "alloy", "off",
            null, "tts-1", "whisper-1",
            null, null, null,
            null, "nova-2");
}
