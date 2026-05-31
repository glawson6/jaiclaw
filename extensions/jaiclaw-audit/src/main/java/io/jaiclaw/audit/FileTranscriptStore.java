package io.jaiclaw.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * File-based {@link TranscriptStore} that persists transcripts as JSON files
 * in a date-partitioned, tenant-scoped directory structure.
 *
 * <p>Layout:
 * <pre>
 *   {storeDir}/
 *     {tenantId}/           (or "_default" for single-tenant)
 *       2026-05-29/
 *         {sessionId}.json
 * </pre>
 */
public class FileTranscriptStore implements TranscriptStore {

    private static final Logger log = LoggerFactory.getLogger(FileTranscriptStore.class);
    private static final String DEFAULT_TENANT_DIR = "_default";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path storeDir;

    public FileTranscriptStore(Path storeDir) {
        this.storeDir = storeDir;
    }

    @Override
    public void save(TranscriptSession session) {
        try {
            String tenantDir = session.tenantId() != null ? session.tenantId() : DEFAULT_TENANT_DIR;
            LocalDate date = session.startTime().atZone(ZoneOffset.UTC).toLocalDate();
            Path file = storeDir.resolve(tenantDir).resolve(date.toString()).resolve(session.sessionId() + ".json");
            Files.createDirectories(file.getParent());
            String json = MAPPER.writeValueAsString(session);
            Files.writeString(file, json);
            log.debug("Saved transcript for session: {}", session.sessionId());
        } catch (IOException e) {
            log.error("Failed to save transcript for session {}: {}", session.sessionId(), e.getMessage());
        }
    }

    @Override
    public Optional<TranscriptSession> load(String sessionId) {
        try {
            Path file = findFile(sessionId);
            if (file != null && Files.exists(file)) {
                String json = Files.readString(file);
                TranscriptSession session = MAPPER.readValue(json, TranscriptSession.class);
                return Optional.of(session);
            }
        } catch (IOException e) {
            log.error("Failed to load transcript for session {}: {}", sessionId, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<String> list(String tenantId, int limit) {
        String tenantDir = tenantId != null ? tenantId : DEFAULT_TENANT_DIR;
        Path tenantPath = storeDir.resolve(tenantDir);

        if (!Files.isDirectory(tenantPath)) {
            return List.of();
        }

        try (Stream<Path> dateDirs = Files.list(tenantPath)) {
            List<String> sessionIds = new ArrayList<>();
            List<Path> sortedDirs = dateDirs
                    .filter(Files::isDirectory)
                    .sorted(Collections.reverseOrder())
                    .toList();

            for (Path dateDir : sortedDirs) {
                if (sessionIds.size() >= limit) break;
                try (Stream<Path> files = Files.list(dateDir)) {
                    List<Path> jsonFiles = files
                            .filter(f -> f.toString().endsWith(".json"))
                            .sorted(Collections.reverseOrder())
                            .toList();
                    for (Path file : jsonFiles) {
                        if (sessionIds.size() >= limit) break;
                        String fileName = file.getFileName().toString();
                        sessionIds.add(fileName.substring(0, fileName.length() - 5)); // strip .json
                    }
                }
            }
            return sessionIds;
        } catch (IOException e) {
            log.error("Failed to list transcripts: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean delete(String sessionId) {
        try {
            Path file = findFile(sessionId);
            if (file != null && Files.exists(file)) {
                Files.delete(file);
                log.debug("Deleted transcript for session: {}", sessionId);
                return true;
            }
        } catch (IOException e) {
            log.error("Failed to delete transcript for session {}: {}", sessionId, e.getMessage());
        }
        return false;
    }

    /**
     * Search all tenant/date directories for a session file.
     */
    private Path findFile(String sessionId) throws IOException {
        String fileName = sessionId + ".json";
        if (!Files.isDirectory(storeDir)) return null;

        try (Stream<Path> tenantDirs = Files.list(storeDir)) {
            List<Path> tenants = tenantDirs.filter(Files::isDirectory).toList();
            for (Path tenantDir : tenants) {
                try (Stream<Path> dateDirs = Files.list(tenantDir)) {
                    List<Path> dates = dateDirs.filter(Files::isDirectory).toList();
                    for (Path dateDir : dates) {
                        Path candidate = dateDir.resolve(fileName);
                        if (Files.exists(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }
}
