package io.jaiclaw.memory;

import io.jaiclaw.core.model.Message;
import io.jaiclaw.core.tenant.TenantGuard;
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
 * <p>
 * In SINGLE mode: {@code sessions/{sessionKey}.jsonl}.
 * In MULTI mode: {@code sessions/{tenantId}/{sessionKey}.jsonl}.
 */
public class SessionTranscriptStore {

    private static final Logger log = LoggerFactory.getLogger(SessionTranscriptStore.class);

    private final Path baseDir;
    private final TenantGuard tenantGuard;

    public SessionTranscriptStore(Path workspaceDir) {
        this(workspaceDir, null);
    }

    public SessionTranscriptStore(Path workspaceDir, TenantGuard tenantGuard) {
        this.baseDir = workspaceDir;
        this.tenantGuard = tenantGuard;
    }

    private Path resolveSessionsDir() {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            return baseDir.resolve("sessions").resolve(tenantGuard.resolveTenantPrefix());
        }
        return baseDir.resolve("sessions");
    }

    public void appendMessage(String sessionKey, Message message) {
        try {
            Path sessionsDir = resolveSessionsDir();
            Files.createDirectories(sessionsDir);
            Path transcriptFile = sessionsDir.resolve(sanitizeFileName(sessionKey) + ".jsonl");

            String role = switch (message) {
                case io.jaiclaw.core.model.UserMessage u -> "user";
                case io.jaiclaw.core.model.AssistantMessage a -> "assistant";
                case io.jaiclaw.core.model.SystemMessage s -> "system";
                case io.jaiclaw.core.model.ToolResultMessage t -> "tool";
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
        Path transcriptFile = resolveSessionsDir().resolve(sanitizeFileName(sessionKey) + ".jsonl");
        if (!Files.exists(transcriptFile)) return List.of();
        try {
            return Files.readAllLines(transcriptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read transcript {}: {}", sessionKey, e.getMessage());
            return List.of();
        }
    }

    public boolean exists(String sessionKey) {
        return Files.exists(resolveSessionsDir().resolve(sanitizeFileName(sessionKey) + ".jsonl"));
    }

    public Path getSessionsDir() {
        return resolveSessionsDir();
    }

    private String sanitizeFileName(String sessionKey) {
        return sessionKey.replaceAll("[^a-zA-Z0-9:._-]", "_");
    }
}
