package io.jclaw.channel.sms;

/**
 * Configuration for the SMS channel adapter (Twilio).
 *
 * @param accountSid   Twilio account SID
 * @param authToken    Twilio auth token
 * @param fromNumber   Twilio phone number for outbound messages
 * @param webhookPath  path for inbound webhook (default: /webhooks/sms)
 * @param enabled      whether the adapter is enabled
 */
public record SmsConfig(
        String accountSid,
        String authToken,
        String fromNumber,
        String webhookPath,
        boolean enabled
) {
    public SmsConfig {
        if (accountSid == null) accountSid = "";
        if (authToken == null) authToken = "";
        if (fromNumber == null) fromNumber = "";
        if (webhookPath == null || webhookPath.isBlank()) webhookPath = "/webhooks/sms";
    }

    public static final SmsConfig DISABLED = new SmsConfig("", "", "", "/webhooks/sms", false);
}
