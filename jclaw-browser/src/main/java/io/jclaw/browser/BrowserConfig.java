package io.jclaw.browser;

/**
 * Configuration for the browser automation service.
 */
public record BrowserConfig(
        boolean enabled,
        boolean headless,
        String profilesDir,
        String downloadDir,
        int timeoutMs,
        int viewportWidth,
        int viewportHeight
) {
    public BrowserConfig {
        if (profilesDir == null) profilesDir = System.getProperty("user.home") + "/.jclaw/browser-profiles";
        if (downloadDir == null) downloadDir = System.getProperty("java.io.tmpdir") + "/jclaw-downloads";
        if (timeoutMs <= 0) timeoutMs = 30000;
        if (viewportWidth <= 0) viewportWidth = 1280;
        if (viewportHeight <= 0) viewportHeight = 720;
    }

    public static final BrowserConfig DEFAULT = new BrowserConfig(
            false, true, null, null, 30000, 1280, 720);
}
