package io.jaiclaw.channel.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the WhatsApp channel adapter.
 *
 * <p><strong>This adapter is Camel-based and serves no HTTP webhook.</strong>
 * Inbound messages arrive over the {@code whatsapp:} Camel component (see
 * {@link WhatsAppChannelAutoConfiguration}), not through JaiClaw's webhook
 * dispatcher.
 *
 * <p>{@code verifyToken} and {@code webhookPath} are therefore <em>inert</em>:
 * nothing reads them, and no endpoint exists at {@code webhookPath} to protect.
 * They are retained only so existing configuration keeps binding. Setting
 * {@code verifyToken} provides <strong>no</strong> security — in particular,
 * Meta's {@code X-Hub-Signature-256} is not verified anywhere, because there is
 * no request for it to be verified on.
 *
 * <p>If a webhook ingress is added later it must verify that header, and these
 * two fields become live at the same time — not before.
 *
 * @param phoneNumberId WhatsApp Business API phone number ID
 * @param accessToken   Meta Cloud API access token
 * @param verifyToken   <strong>inert</strong> — see the class note above
 * @param webhookPath   <strong>inert</strong> — no controller serves this path
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
