package io.jclaw.examples.meeting;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool for transcribing meeting audio.
 * In production, this would use VoiceService STT (speech-to-text).
 */
@Component
public class TranscriptionTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "transcribe_meeting",
                "Transcribe a meeting recording from an audio file",
                "meeting",
                """
                {
                  "type": "object",
                  "properties": {
                    "audio_url": { "type": "string", "description": "URL or path to the audio file" },
                    "language": { "type": "string", "description": "Language code (e.g., en-US)", "default": "en-US" }
                  },
                  "required": ["audio_url"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String audioUrl = (String) parameters.get("audio_url");
        // Simulated — in production, use VoiceService STT
        String transcript = String.format("""
                {
                  "audio_url": "%s",
                  "duration_minutes": 45,
                  "speakers": ["Alice", "Bob", "Charlie"],
                  "transcript": [
                    {"speaker": "Alice", "time": "00:00", "text": "Let's start with the Q3 review."},
                    {"speaker": "Bob", "time": "00:30", "text": "Revenue is up 15%% from last quarter."},
                    {"speaker": "Charlie", "time": "01:15", "text": "Customer churn dropped to 2.1%%."},
                    {"speaker": "Alice", "time": "02:00", "text": "Great. Let's discuss the product roadmap next."},
                    {"speaker": "Bob", "time": "02:30", "text": "We need to prioritize the mobile app launch."}
                  ]
                }""", audioUrl);
        return new ToolResult.Success(transcript);
    }
}
