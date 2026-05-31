package io.jaiclaw.core.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Typed accessor for JaiClaw's localized messages using {@link ResourceBundle}.
 *
 * <p>This class is pure Java with no Spring dependency, suitable for use in
 * {@code jaiclaw-core}. The resource bundles are loaded from
 * {@code i18n/messages*.properties} on the classpath.
 *
 * <p>Thread-safe: each instance is immutable once created. Use {@link #forLocale(Locale)}
 * or {@link #forLocale(JaiClawLocale)} to create instances for specific locales.
 */
public final class JaiClawMessages {

    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private static final JaiClawMessages DEFAULT = new JaiClawMessages(Locale.ENGLISH);

    private final ResourceBundle bundle;
    private final Locale locale;

    private JaiClawMessages(Locale locale) {
        this.locale = locale;
        this.bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }

    /**
     * Get the default (English) messages instance.
     */
    public static JaiClawMessages getDefault() {
        return DEFAULT;
    }

    /**
     * Get messages for a specific locale.
     */
    public static JaiClawMessages forLocale(Locale locale) {
        if (locale == null || Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            return DEFAULT;
        }
        return new JaiClawMessages(locale);
    }

    /**
     * Get messages for a specific JaiClawLocale.
     */
    public static JaiClawMessages forLocale(JaiClawLocale jaiClawLocale) {
        if (jaiClawLocale == null) {
            return DEFAULT;
        }
        return forLocale(jaiClawLocale.locale());
    }

    /**
     * Get the locale this instance uses.
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Get a message by key. Returns the key itself if not found.
     */
    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Get a message by key with positional arguments using {@link MessageFormat}.
     * Returns the key itself if the key is not found.
     */
    public String get(String key, Object... args) {
        String pattern = get(key);
        if (pattern.equals(key)) {
            return key;
        }
        return MessageFormat.format(pattern, args);
    }

    // --- Typed accessors for common message categories ---

    // Error messages
    public String errorSessionNotFound(String sessionId) {
        return get("error.session.not.found", sessionId);
    }

    public String errorToolNotFound(String toolName) {
        return get("error.tool.not.found", toolName);
    }

    public String errorToolExecutionFailed(String toolName) {
        return get("error.tool.execution.failed", toolName);
    }

    public String errorChannelUnavailable(String channel) {
        return get("error.channel.unavailable", channel);
    }

    public String errorAuthenticationRequired() {
        return get("error.authentication.required");
    }

    public String errorAccessDenied() {
        return get("error.access.denied");
    }

    public String errorRateLimited() {
        return get("error.rate.limited");
    }

    // Status messages
    public String statusAgentReady() {
        return get("status.agent.ready");
    }

    public String statusProcessing() {
        return get("status.processing");
    }

    public String statusChannelConnected(String channel) {
        return get("status.channel.connected", channel);
    }

    public String statusChannelDisconnected(String channel) {
        return get("status.channel.disconnected", channel);
    }

    // Tool descriptions
    public String toolWebSearchDescription() {
        return get("tool.web.search.description");
    }

    public String toolWebFetchDescription() {
        return get("tool.web.fetch.description");
    }

    public String toolShellExecDescription() {
        return get("tool.shell.exec.description");
    }

    public String toolImageGenDescription() {
        return get("tool.image.gen.description");
    }

    // System prompt sections
    public String promptSystemPrefix() {
        return get("prompt.system.prefix");
    }

    public String promptNoToolsAvailable() {
        return get("prompt.no.tools.available");
    }

    public String promptMemoryContext() {
        return get("prompt.memory.context");
    }
}
