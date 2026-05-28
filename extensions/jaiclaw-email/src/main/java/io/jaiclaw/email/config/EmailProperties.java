package io.jaiclaw.email.config;

/**
 * Configuration properties for the email extension.
 * Bound from {@code jaiclaw.email.*} in application configuration.
 *
 * @param enabled       whether the email extension is active
 * @param provider      email provider name ("smtp2go" by default)
 * @param defaultFrom   default sender email address
 * @param defaultFromName default sender display name
 * @param smtp2go       SMTP2GO-specific configuration
 */
public record EmailProperties(
        boolean enabled,
        String provider,
        String defaultFrom,
        String defaultFromName,
        Smtp2goConfig smtp2go
) {
    public EmailProperties {
        if (provider == null) provider = "smtp2go";
        if (smtp2go == null) smtp2go = new Smtp2goConfig(null, "https://api.smtp2go.com/v3", 10);
    }

    /**
     * SMTP2GO provider configuration.
     *
     * @param apiKey         SMTP2GO API key
     * @param apiUrl         SMTP2GO API base URL
     * @param timeoutSeconds HTTP request timeout in seconds
     */
    public record Smtp2goConfig(
            String apiKey,
            String apiUrl,
            int timeoutSeconds
    ) {
        public Smtp2goConfig {
            if (apiUrl == null) apiUrl = "https://api.smtp2go.com/v3";
            if (timeoutSeconds <= 0) timeoutSeconds = 10;
        }
    }
}
