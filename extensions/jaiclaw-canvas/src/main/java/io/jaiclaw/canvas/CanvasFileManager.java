package io.jaiclaw.canvas;

import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages agent-generated HTML files for the canvas.
 * <p>
 * In SINGLE mode: {@code {canvasDir}/{id}.html}.
 * In MULTI mode: {@code {canvasDir}/{tenantId}/{id}.html}.
 */
public class CanvasFileManager {

    private static final Logger log = LoggerFactory.getLogger(CanvasFileManager.class);

    private final Path canvasDir;
    private final TenantGuard tenantGuard;

    public CanvasFileManager() {
        this(null, null);
    }

    public CanvasFileManager(Path canvasDir) {
        this(canvasDir, null);
    }

    public CanvasFileManager(Path canvasDir, TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
        try {
            if (canvasDir == null) {
                this.canvasDir = Files.createTempDirectory("jaiclaw-canvas-");
            } else {
                this.canvasDir = canvasDir;
                Files.createDirectories(canvasDir);
            }
            log.info("Canvas files directory: {}", this.canvasDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create canvas directory", e);
        }
    }

    private Path resolveDir() {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            Path tenantDir = canvasDir.resolve(tenantGuard.resolveTenantPrefix());
            try {
                Files.createDirectories(tenantDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create tenant canvas directory", e);
            }
            return tenantDir;
        }
        return canvasDir;
    }

    public String writeHtml(String html) {
        return writeHtml(UUID.randomUUID().toString(), html);
    }

    public String writeHtml(String id, String html) {
        String fileName = id + ".html";
        try {
            Files.writeString(resolveDir().resolve(fileName), html, StandardCharsets.UTF_8);
            return fileName;
        } catch (IOException e) {
            log.error("Failed to write canvas HTML {}: {}", fileName, e.getMessage());
            throw new RuntimeException("Failed to write canvas file", e);
        }
    }

    public Optional<String> readHtml(String fileName) {
        Path file = resolveDir().resolve(fileName);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to read canvas file {}: {}", fileName, e.getMessage());
            return Optional.empty();
        }
    }

    public Path getCanvasDir() {
        return canvasDir;
    }
}
