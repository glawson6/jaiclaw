package io.jaiclaw.channel.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the WhatsApp channel adapter.
 *
 * @param phoneNumberId WhatsApp Business API phone number ID
 * @param accessToken   Meta Cloud API access token
 * @param verifyToken   webhook verification token
 * @param webhookPath   webhook endpoint path (default: /webhook/whatsapp)
 */
@ConfigurationProperties(prefix = "jaiclaw.channels.whatsapp")
public record WhatsAppProperties(
        String phoneNumberId,
        String accessToken,
        String verifyToken,
        String webhookPath
) {
    public WhatsAppProperties {
        if (webhookPath == null || webhookPath.isBlank()) {
            webhookPath = "/webhook/whatsapp";
        }
    }
}
