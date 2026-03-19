package io.jclaw.canvas;

/**
 * Configuration for the canvas host server.
 */
public record CanvasConfig(
        boolean enabled,
        int port,
        String host,
        boolean liveReload
) {
    public CanvasConfig {
        if (port <= 0) port = 18793;
        if (host == null) host = "127.0.0.1";
    }

    public static final CanvasConfig DEFAULT = new CanvasConfig(false, 18793, "127.0.0.1", true);
}
