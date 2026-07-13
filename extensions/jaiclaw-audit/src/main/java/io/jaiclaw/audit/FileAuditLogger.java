package io.jaiclaw.audit;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Persistent {@link AuditLogger} that writes events as JSON-lines files.
 *
 * <p>File layout:
 * <pre>
 *   {storeDir}/
 *     {tenantId}/         (or "_default" for single-tenant)
 *       2026-05-29.jsonl
 *       2026-05-30.jsonl
 * </pre>
 *
 * <p>Each line is a complete JSON object representing one {@link AuditEvent}.
 * In multi-tenant mode, events are stored in tenant-specific subdirectories.
 */
public class FileAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(FileAuditLogger.class);
    private static final String DEFAULT_TENANT_DIR = "_default";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Path storeDir;
    private final TenantGuard tenantGuard;

    public FileAuditLogger(Path storeDir) {
        this(storeDir, null);
    }

    public FileAuditLogger(Path storeDir, TenantGuard tenantGuard) {
        this.storeDir = storeDir;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public void log(AuditEvent event) {
        // Auto-stamp tenantId from TenantGuard if not set
        if (event.tenantId() == null && tenantGuard != null) {
            String currentTenant = tenantGuard.requireTenantIfMulti();
            if (currentTenant != null) {
                event = new AuditEvent(event.id(), event.timestamp(), currentTenant,
                        event.actor(), event.action(), event.resource(),
                        event.outcome(), event.details());
            }
        }

        try {
            Path file = resolveFile(event.tenantId(), event.timestamp().atZone(ZoneOffset.UTC).toLocalDate());
            Files.createDirectories(file.getParent());
            String json = MAPPER.writeValueAsString(event);
            Files.writeString(file, json + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write audit event {}: {}", event.id(), e.getMessage());
        }
    }

    @Override
    public List<AuditEvent> query(String tenantId, int limit) {
        String effectiveTenant = resolveEffectiveTenant(tenantId);
        Path tenantDir = storeDir.resolve(effectiveTenant != null ? effectiveTenant : DEFAULT_TENANT_DIR);

        if (!Files.isDirectory(tenantDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(tenantDir)) {
            List<Path> jsonlFiles = files
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .sorted(Collections.reverseOrder())
                    .toList();

            List<AuditEvent> results = new java.util.ArrayList<>();
            for (Path file : jsonlFiles) {
                if (results.size() >= limit) break;
                List<String> lines = Files.readAllLines(file);
                // Reverse to get most-recent-first within a file
                Collections.reverse(lines);
                for (String line : lines) {
                    if (results.size() >= limit) break;
                    if (!line.isBlank()) {
                        AuditEvent event = MAPPER.readValue(line, AuditEvent.class);
                        results.add(event);
                    }
                }
            }
            return results;
        } catch (IOException e) {
            log.error("Failed to query audit events: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<AuditEvent> findById(String id) {
        String effectiveTenant = resolveEffectiveTenant(null);
        Path tenantDir = storeDir.resolve(effectiveTenant != null ? effectiveTenant : DEFAULT_TENANT_DIR);

        if (!Files.isDirectory(tenantDir)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(tenantDir)) {
            List<Path> jsonlFiles = files
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .toList();

            for (Path file : jsonlFiles) {
                for (String line : Files.readAllLines(file)) {
                    if (!line.isBlank() && line.contains("\"" + id + "\"")) {
                        AuditEvent event = MAPPER.readValue(line, AuditEvent.class);
                        if (event.id().equals(id)) {
                            return Optional.of(event);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to find audit event {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public long count(String tenantId) {
        String effectiveTenant = resolveEffectiveTenant(tenantId);
        Path tenantDir = storeDir.resolve(effectiveTenant != null ? effectiveTenant : DEFAULT_TENANT_DIR);

        if (!Files.isDirectory(tenantDir)) {
            return 0;
        }

        try (Stream<Path> files = Files.list(tenantDir)) {
            long total = 0;
            List<Path> jsonlFiles = files
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .toList();
            for (Path file : jsonlFiles) {
                total += Files.lines(file).filter(l -> !l.isBlank()).count();
            }
            return total;
        } catch (IOException e) {
            log.error("Failed to count audit events: {}", e.getMessage());
            return 0;
        }
    }

    private Path resolveFile(String tenantId, LocalDate date) {
        String tenantDir = tenantId != null ? tenantId : DEFAULT_TENANT_DIR;
        return storeDir.resolve(tenantDir).resolve(date + ".jsonl");
    }

    private String resolveEffectiveTenant(String tenantId) {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantId;
    }
}
