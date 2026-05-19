package io.jaiclaw.channel.telegram;

/**
 * Selects which {@link TelegramHttpClient} implementation to use for
 * Telegram Bot API calls.
 *
 * <p>Configured via {@code jaiclaw.channels.telegram.http-client}.
 *
 * <ul>
 *   <li>{@link #JDK} — (default) Uses {@code java.net.http.HttpClient}.
 *       Zero additional dependencies, virtual-thread friendly.</li>
 *   <li>{@link #REST_TEMPLATE} — Uses Spring's {@code RestTemplate} with
 *       {@code SimpleClientHttpRequestFactory} configured with timeouts.</li>
 *   <li>{@link #WEB_CLIENT} — Uses Spring WebFlux's reactive {@code WebClient}.
 *       Requires {@code spring-webflux} on the classpath.</li>
 * </ul>
 */
public enum TelegramHttpClientType {

    JDK,
    REST_TEMPLATE,
    WEB_CLIENT;

    /**
     * Parse a config string (case-insensitive, supports kebab-case).
     * Returns {@link #JDK} for null/blank/unrecognized values.
     */
    public static TelegramHttpClientType fromString(String value) {
        if (value == null || value.isBlank()) return JDK;
        return switch (value.strip().toLowerCase().replace("-", "_").replace(" ", "_")) {
            case "jdk" -> JDK;
            case "rest_template", "resttemplate" -> REST_TEMPLATE;
            case "web_client", "webclient" -> WEB_CLIENT;
            default -> JDK;
        };
    }
}
