package io.jclaw.voice;

import io.jclaw.core.model.AudioResult;
import io.jclaw.core.model.TranscriptionResult;
import io.jclaw.voice.stt.SttProvider;
import io.jclaw.voice.tts.TtsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates TTS/STT operations with provider fallback chain.
 */
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    private final List<TtsProvider> ttsProviders;
    private final List<SttProvider> sttProviders;
    private final TtsDirectiveParser directiveParser;
    private final String defaultVoice;

    public VoiceService(List<TtsProvider> ttsProviders, List<SttProvider> sttProviders,
                        String defaultVoice) {
        this.ttsProviders = ttsProviders;
        this.sttProviders = sttProviders;
        this.directiveParser = new TtsDirectiveParser();
        this.defaultVoice = defaultVoice != null ? defaultVoice : "alloy";
    }

    public Optional<AudioResult> synthesize(String text) {
        return synthesize(text, defaultVoice);
    }

    public Optional<AudioResult> synthesize(String text, String voice) {
        for (TtsProvider provider : ttsProviders) {
            try {
                AudioResult result = provider.synthesize(text, voice, Map.of());
                return Optional.of(result);
            } catch (Exception e) {
                log.warn("TTS provider {} failed, trying next: {}", provider.providerId(), e.getMessage());
            }
        }
        log.error("All TTS providers failed for text of length {}", text.length());
        return Optional.empty();
    }

    public Optional<TranscriptionResult> transcribe(byte[] audioBytes, String mimeType) {
        for (SttProvider provider : sttProviders) {
            try {
                TranscriptionResult result = provider.transcribe(audioBytes, mimeType);
                return Optional.of(result);
            } catch (Exception e) {
                log.warn("STT provider {} failed, trying next: {}", provider.providerId(), e.getMessage());
            }
        }
        log.error("All STT providers failed for audio of {} bytes", audioBytes.length);
        return Optional.empty();
    }

    public List<TtsDirectiveParser.TtsSegment> parseDirectives(String text) {
        return directiveParser.parse(text);
    }

    public String stripDirectives(String text) {
        return directiveParser.stripDirectives(text);
    }
}
