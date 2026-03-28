package io.jaiclaw.browser;

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
        if (profilesDir == null) profilesDir = System.getProperty("user.home") + "/.jaiclaw/browser-profiles";
        if (downloadDir == null) downloadDir = System.getProperty("java.io.tmpdir") + "/jaiclaw-downloads";
        if (timeoutMs <= 0) timeoutMs = 30000;
        if (viewportWidth <= 0) viewportWidth = 1280;
        if (viewportHeight <= 0) viewportHeight = 720;
    }

    public static final BrowserConfig DEFAULT = new BrowserConfig(
            false, true, null, null, 30000, 1280, 720);

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean enabled;
        private boolean headless;
        private String profilesDir;
        private String downloadDir;
        private int timeoutMs;
        private int viewportWidth;
        private int viewportHeight;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder headless(boolean headless) { this.headless = headless; return this; }
        public Builder profilesDir(String profilesDir) { this.profilesDir = profilesDir; return this; }
        public Builder downloadDir(String downloadDir) { this.downloadDir = downloadDir; return this; }
        public Builder timeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; return this; }
        public Builder viewportWidth(int viewportWidth) { this.viewportWidth = viewportWidth; return this; }
        public Builder viewportHeight(int viewportHeight) { this.viewportHeight = viewportHeight; return this; }

        public BrowserConfig build() {
            return new BrowserConfig(
                    enabled, headless, profilesDir, downloadDir, timeoutMs, viewportWidth, viewportHeight);
        }
    }
}
