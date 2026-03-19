package io.jclaw.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Represents a single browser tab session with navigation, interaction, and screenshot capabilities.
 * Wraps a Playwright Page instance using reflection to avoid compile-time dependency.
 */
public class BrowserSession {

    private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);

    private final String id;
    private final Object browser;
    private final BrowserConfig config;
    private Object page;
    private String currentUrl = "";
    private String currentTitle = "";

    BrowserSession(String id, Object browser, BrowserConfig config) {
        this.id = id;
        this.browser = browser;
        this.config = config;
        initPage();
    }

    public String navigate(String url) {
        try {
            page.getClass().getMethod("navigate", String.class).invoke(page, url);
            currentUrl = (String) page.getClass().getMethod("url").invoke(page);
            currentTitle = (String) page.getClass().getMethod("title").invoke(page);
            return readPageContent(null);
        } catch (Exception e) {
            log.error("Navigation to {} failed: {}", url, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    public String click(String selector) {
        try {
            page.getClass().getMethod("click", String.class).invoke(page, selector);
            currentUrl = (String) page.getClass().getMethod("url").invoke(page);
            return "Clicked: " + selector;
        } catch (Exception e) {
            return "Error clicking '" + selector + "': " + e.getMessage();
        }
    }

    public String type(String selector, String text) {
        try {
            page.getClass().getMethod("fill", String.class, String.class).invoke(page, selector, text);
            return "Typed into: " + selector;
        } catch (Exception e) {
            return "Error typing into '" + selector + "': " + e.getMessage();
        }
    }

    public String screenshot() {
        try {
            byte[] bytes = (byte[]) page.getClass().getMethod("screenshot").invoke(page);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return "Error taking screenshot: " + e.getMessage();
        }
    }

    public String evaluate(String javascript) {
        try {
            Object result = page.getClass().getMethod("evaluate", String.class).invoke(page, javascript);
            return result != null ? result.toString() : "null";
        } catch (Exception e) {
            return "Error evaluating JS: " + e.getMessage();
        }
    }

    public String readPageContent(String selector) {
        try {
            String script = selector != null
                    ? "document.querySelector('" + selector + "')?.innerText || ''"
                    : "document.body.innerText";
            Object result = page.getClass().getMethod("evaluate", String.class).invoke(page, script);
            String text = result != null ? result.toString() : "";

            String url = (String) page.getClass().getMethod("url").invoke(page);
            String title = (String) page.getClass().getMethod("title").invoke(page);
            return "URL: " + url + "\nTitle: " + title + "\n\n" + text;
        } catch (Exception e) {
            return "Error reading page: " + e.getMessage();
        }
    }

    public String getId() {
        return id;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    void close() {
        try {
            if (page != null) {
                page.getClass().getMethod("close").invoke(page);
            }
        } catch (Exception e) {
            log.warn("Failed to close browser session {}: {}", id, e.getMessage());
        }
    }

    private void initPage() {
        try {
            page = browser.getClass().getMethod("newPage").invoke(browser);

            Class<?> setViewportSizeClass = page.getClass();
            setViewportSizeClass.getMethod("setViewportSize", int.class, int.class)
                    .invoke(page, config.viewportWidth(), config.viewportHeight());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create browser page", e);
        }
    }
}
