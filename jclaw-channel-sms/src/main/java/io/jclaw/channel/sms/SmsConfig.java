package io.jclaw.channel.sms;

import java.util.Set;

/**
 * Configuration for the SMS channel adapter (Twilio).
 *
 * @param accountSid        Twilio account SID
 * @param authToken         Twilio auth token
 * @param fromNumber        Twilio phone number for outbound messages
 * @param webhookPath       path for inbound webhook (default: /webhooks/sms)
 * @param enabled           whether the adapter is enabled
 * @param allowedSenderIds  set of allowed sender phone numbers (empty = allow all)
 */
public record SmsConfig(
        String accountSid,
        String authToken,
        String fromNumber,
        String webhookPath,
        boolean enabled,
        Set<String> allowedSenderIds
) {
    public SmsConfig {
        if (accountSid == null) accountSid = "";
        if (authToken == null) authToken = "";
        if (fromNumber == null) fromNumber = "";
        if (webhookPath == null || webhookPath.isBlank()) webhookPath = "/webhooks/sms";
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    /** Backwards-compatible 5-arg constructor. */
    public SmsConfig(String accountSid, String authToken, String fromNumber,
                     String webhookPath, boolean enabled) {
        this(accountSid, authToken, fromNumber, webhookPath, enabled, Set.of());
    }

    /**
     * Returns true if the given sender is allowed.
     * An empty allowedSenderIds set means all senders are allowed.
     */
    public boolean isSenderAllowed(String sender) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(sender);
    }

    public static final SmsConfig DISABLED = new SmsConfig("", "", "", "/webhooks/sms", false, Set.of());
}
