package io.jaiclaw.canvas;

import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates canvas operations: create content, serve, notify clients.
 * <p>
 * Canvas state (current file, visibility) is per-session to support multi-tenant
 * deployments where each session has its own canvas view.
 */
public class CanvasService {

    private static final Logger log = LoggerFactory.getLogger(CanvasService.class);
    private static final String DEFAULT_SESSION = "__default__";

    private final CanvasConfig config;
    private final CanvasFileManager fileManager;
    private final TenantGuard tenantGuard;
    private final ConcurrentHashMap<String, CanvasState> sessionStates = new ConcurrentHashMap<>();

    public CanvasService(CanvasConfig config, CanvasFileManager fileManager) {
        this(config, fileManager, null);
    }

    public CanvasService(CanvasConfig config, CanvasFileManager fileManager, TenantGuard tenantGuard) {
        this.config = config;
        this.fileManager = fileManager;
        this.tenantGuard = tenantGuard;
    }

    private String scopedKey(String sessionKey) {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            return tenantGuard.resolveTenantPrefix() + ":" + sessionKey;
        }
        return sessionKey;
    }

    public String present(String html) {
        return present(DEFAULT_SESSION, html);
    }

    public String present(String sessionKey, String html) {
        String scoped = scopedKey(sessionKey);
        String fileName = fileManager.writeHtml(html);
        sessionStates.put(scoped, new CanvasState(fileName, true));
        String url = String.format("http://%s:%d/%s", config.host(), config.port(), fileName);
        log.info("Canvas presented for session {}: {}", scoped, url);
        return url;
    }

    public void hide() {
        hide(DEFAULT_SESSION);
    }

    public void hide(String sessionKey) {
        String scoped = scopedKey(sessionKey);
        sessionStates.computeIfPresent(scoped, (k, s) -> new CanvasState(s.currentFile(), false));
        log.info("Canvas hidden for session {}", scoped);
    }

    public Optional<String> getCurrentContent() {
        return getCurrentContent(DEFAULT_SESSION);
    }

    public Optional<String> getCurrentContent(String sessionKey) {
        CanvasState state = sessionStates.get(scopedKey(sessionKey));
        if (state == null || state.currentFile() == null) return Optional.empty();
        return fileManager.readHtml(state.currentFile());
    }

    public String getCurrentUrl() {
        return getCurrentUrl(DEFAULT_SESSION);
    }

    public String getCurrentUrl(String sessionKey) {
        CanvasState state = sessionStates.get(scopedKey(sessionKey));
        if (state == null || state.currentFile() == null) return "";
        return String.format("http://%s:%d/%s", config.host(), config.port(), state.currentFile());
    }

    public boolean isVisible() {
        return isVisible(DEFAULT_SESSION);
    }

    public boolean isVisible(String sessionKey) {
        CanvasState state = sessionStates.get(scopedKey(sessionKey));
        return state != null && state.visible();
    }

    public CanvasConfig getConfig() {
        return config;
    }

    private record CanvasState(String currentFile, boolean visible) {}
}
