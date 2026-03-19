package io.jclaw.canvas;

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
 * Files are written to a temp directory and served by the canvas host.
 */
public class CanvasFileManager {

    private static final Logger log = LoggerFactory.getLogger(CanvasFileManager.class);

    private final Path canvasDir;

    public CanvasFileManager() {
        try {
            this.canvasDir = Files.createTempDirectory("jclaw-canvas-");
            log.info("Canvas files directory: {}", canvasDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create canvas temp directory", e);
        }
    }

    public CanvasFileManager(Path canvasDir) {
        this.canvasDir = canvasDir;
        try {
            Files.createDirectories(canvasDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create canvas directory", e);
        }
    }

    public String writeHtml(String html) {
        return writeHtml(UUID.randomUUID().toString(), html);
    }

    public String writeHtml(String id, String html) {
        String fileName = id + ".html";
        try {
            Files.writeString(canvasDir.resolve(fileName), html, StandardCharsets.UTF_8);
            return fileName;
        } catch (IOException e) {
            log.error("Failed to write canvas HTML {}: {}", fileName, e.getMessage());
            throw new RuntimeException("Failed to write canvas file", e);
        }
    }

    public Optional<String> readHtml(String fileName) {
        Path file = canvasDir.resolve(fileName);
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
