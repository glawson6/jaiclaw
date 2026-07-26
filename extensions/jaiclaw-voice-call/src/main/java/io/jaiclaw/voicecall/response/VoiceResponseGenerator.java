package io.jaiclaw.voicecall.response;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts spoken text from AI agent responses.
 * Expects JSON format {@code {"spoken":"..."}} with plain-text fallback.
 */
public class VoiceResponseGenerator {

    private static final Logger log = LoggerFactory.getLogger(VoiceResponseGenerator.class);
    private final ObjectMapper objectMapper;

    public VoiceResponseGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Extract the spoken text from an agent response.
     * Attempts JSON parsing for structured responses, falls back to plain text.
     *
     * @param agentResponse raw response from the agent
     * @return text suitable for TTS
     */
    public String extractSpokenText(String agentResponse) {
        if (agentResponse == null || agentResponse.isBlank()) {
            return "";
        }

        String trimmed = agentResponse.trim();

        // Try JSON format: {"spoken": "..."}
        if (trimmed.startsWith("{")) {
            try {
                JsonNode json = objectMapper.readTree(trimmed);
                JsonNode spoken = json.path("spoken");
                if (!spoken.isMissingNode() && spoken.isTextual()) {
                    return spoken.asText();
                }
                // Try "text" field as fallback
                JsonNode text = json.path("text");
                if (!text.isMissingNode() && text.isTextual()) {
                    return text.asText();
                }
            } catch (Exception e) {
                log.debug("Response is not valid JSON, using as plain text");
            }
        }

        // Strip markdown formatting that doesn't work in speech
        String cleaned = trimmed
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")  // bold
                .replaceAll("\\*(.+?)\\*", "$1")          // italic
                .replaceAll("`(.+?)`", "$1")               // inline code
                .replaceAll("^#+\\s+", "")                 // headings
                .replaceAll("\\[(.+?)]\\(.+?\\)", "$1")   // links
                .replaceAll("^[-*]\\s+", "")               // list items
                .trim();

        return cleaned;
    }

    /**
     * Build a voice-specific system prompt that instructs the model to respond
     * in a conversational, speech-friendly format.
     */
    public static String buildVoiceSystemPrompt(String basePrompt) {
        String voiceInstruction = """
                You are in a voice call. Respond conversationally and concisely.
                Keep responses under 2-3 sentences when possible.
                Do not use markdown, code blocks, or formatting.
                Do not use URLs, file paths, or technical references.
                Speak naturally as you would in a phone conversation.
                When providing structured information, describe it conversationally.
                """;

        if (basePrompt != null && !basePrompt.isBlank()) {
            return voiceInstruction + "\n\n" + basePrompt;
        }
        return voiceInstruction;
    }
}
