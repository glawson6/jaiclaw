package io.jclaw.memory;

import io.jclaw.core.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

/**
 * Persists session transcripts as JSONL files.
 * Each line is a JSON object with timestamp, role, and content.
 */
public class SessionTranscriptStore {

    private static final Logger log = LoggerFactory.getLogger(SessionTranscriptStore.class);

    private final Path sessionsDir;

    public SessionTranscriptStore(Path workspaceDir) {
        this.sessionsDir = workspaceDir.resolve("sessions");
    }

    public void appendMessage(String sessionKey, Message message) {
        try {
            Files.createDirectories(sessionsDir);
            Path transcriptFile = sessionsDir.resolve(sanitizeFileName(sessionKey) + ".jsonl");

            String role = switch (message) {
                case io.jclaw.core.model.UserMessage u -> "user";
                case io.jclaw.core.model.AssistantMessage a -> "assistant";
                case io.jclaw.core.model.SystemMessage s -> "system";
                case io.jclaw.core.model.ToolResultMessage t -> "tool";
            };

            String escapedContent = message.content()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");

            String jsonLine = String.format(
                    "{\"ts\":\"%s\",\"role\":\"%s\",\"content\":\"%s\"}",
                    Instant.now(), role, escapedContent);

            Files.writeString(transcriptFile, jsonLine + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to persist transcript for {}: {}", sessionKey, e.getMessage());
        }
    }

    public List<String> readTranscript(String sessionKey) {
        Path transcriptFile = sessionsDir.resolve(sanitizeFileName(sessionKey) + ".jsonl");
        if (!Files.exists(transcriptFile)) return List.of();
        try {
            return Files.readAllLines(transcriptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read transcript {}: {}", sessionKey, e.getMessage());
            return List.of();
        }
    }

    public boolean exists(String sessionKey) {
        return Files.exists(sessionsDir.resolve(sanitizeFileName(sessionKey) + ".jsonl"));
    }

    public Path getSessionsDir() {
        return sessionsDir;
    }

    private String sanitizeFileName(String sessionKey) {
        return sessionKey.replaceAll("[^a-zA-Z0-9:._-]", "_");
    }
}
