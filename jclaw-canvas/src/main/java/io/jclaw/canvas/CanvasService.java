package io.jclaw.canvas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Orchestrates canvas operations: create content, serve, notify clients.
 */
public class CanvasService {

    private static final Logger log = LoggerFactory.getLogger(CanvasService.class);

    private final CanvasConfig config;
    private final CanvasFileManager fileManager;
    private String currentFile;
    private boolean visible;

    public CanvasService(CanvasConfig config, CanvasFileManager fileManager) {
        this.config = config;
        this.fileManager = fileManager;
        this.visible = false;
    }

    public String present(String html) {
        String fileName = fileManager.writeHtml(html);
        this.currentFile = fileName;
        this.visible = true;
        String url = String.format("http://%s:%d/%s", config.host(), config.port(), fileName);
        log.info("Canvas presented: {}", url);
        return url;
    }

    public void hide() {
        this.visible = false;
        log.info("Canvas hidden");
    }

    public Optional<String> getCurrentContent() {
        if (currentFile == null) return Optional.empty();
        return fileManager.readHtml(currentFile);
    }

    public String getCurrentUrl() {
        if (currentFile == null) return "";
        return String.format("http://%s:%d/%s", config.host(), config.port(), currentFile);
    }

    public boolean isVisible() {
        return visible;
    }

    public CanvasConfig getConfig() {
        return config;
    }
}
