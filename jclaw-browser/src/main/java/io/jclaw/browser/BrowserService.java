package io.jclaw.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Playwright browser lifecycle, tab pool, and profile loading.
 * Uses lazy initialization — the browser is not started until the first navigation.
 *
 * <p>This class abstracts the Playwright API behind a simple interface so tools
 * can be tested without a real browser.
 */
public class BrowserService {

    private static final Logger log = LoggerFactory.getLogger(BrowserService.class);

    private final BrowserConfig config;
    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();
    private Object playwright;
    private Object browser;
    private boolean initialized;

    public BrowserService(BrowserConfig config) {
        this.config = config;
    }

    public synchronized BrowserSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            ensureInitialized();
            return new BrowserSession(id, browser, config);
        });
    }

    public BrowserSession getDefaultSession() {
        return getOrCreateSession("default");
    }

    public List<String> listSessions() {
        return List.copyOf(sessions.keySet());
    }

    public void closeSession(String sessionId) {
        BrowserSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }

    public synchronized void shutdown() {
        sessions.values().forEach(BrowserSession::close);
        sessions.clear();
        if (browser != null) {
            try {
                browser.getClass().getMethod("close").invoke(browser);
            } catch (Exception e) {
                log.warn("Failed to close browser: {}", e.getMessage());
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.getClass().getMethod("close").invoke(playwright);
            } catch (Exception e) {
                log.warn("Failed to close playwright: {}", e.getMessage());
            }
            playwright = null;
        }
        initialized = false;
        log.info("Browser service shut down");
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void ensureInitialized() {
        if (initialized) return;
        try {
            Class<?> playwrightClass = Class.forName("com.microsoft.playwright.Playwright");
            playwright = playwrightClass.getMethod("create").invoke(null);
            Object chromium = playwright.getClass().getMethod("chromium").invoke(playwright);

            Class<?> launchOptionsClass = Class.forName("com.microsoft.playwright.BrowserType$LaunchOptions");
            Object launchOptions = launchOptionsClass.getDeclaredConstructor().newInstance();
            launchOptionsClass.getMethod("setHeadless", boolean.class).invoke(launchOptions, config.headless());

            browser = chromium.getClass().getMethod("launch", launchOptionsClass).invoke(chromium, launchOptions);
            initialized = true;
            log.info("Browser service initialized (headless={})", config.headless());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Playwright is not on the classpath. Add com.microsoft.playwright:playwright dependency.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Playwright browser", e);
        }
    }
}
