package io.jclaw.channel.email;

import java.util.Set;

/**
 * Configuration for the email channel adapter.
 *
 * @param provider          email provider: gmail, outlook, or imap
 * @param host              IMAP host for inbound (e.g., imap.gmail.com)
 * @param port              IMAP port (default 993)
 * @param smtpHost          SMTP host for outbound
 * @param smtpPort          SMTP port (default 587)
 * @param username          email account username
 * @param password          email account password or app password
 * @param enabled           whether the adapter is enabled
 * @param pollingInterval   polling interval in seconds for IMAP (default 60)
 * @param folders           IMAP folders to monitor (default: INBOX)
 * @param allowedSenderIds  set of allowed sender email addresses (empty = allow all)
 */
public record EmailConfig(
        String provider,
        String host,
        int port,
        String smtpHost,
        int smtpPort,
        String username,
        String password,
        boolean enabled,
        int pollingInterval,
        String[] folders,
        Set<String> allowedSenderIds
) {
    public EmailConfig {
        if (provider == null) provider = "imap";
        if (host == null) host = "";
        if (port <= 0) port = 993;
        if (smtpHost == null) smtpHost = "";
        if (smtpPort <= 0) smtpPort = 587;
        if (username == null) username = "";
        if (password == null) password = "";
        if (pollingInterval <= 0) pollingInterval = 60;
        if (folders == null || folders.length == 0) folders = new String[]{"INBOX"};
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    /** Backwards-compatible 10-arg constructor. */
    public EmailConfig(String provider, String host, int port, String smtpHost, int smtpPort,
                       String username, String password, boolean enabled, int pollingInterval,
                       String[] folders) {
        this(provider, host, port, smtpHost, smtpPort, username, password, enabled,
                pollingInterval, folders, Set.of());
    }

    /**
     * Returns true if the given sender is allowed.
     * An empty allowedSenderIds set means all senders are allowed.
     */
    public boolean isSenderAllowed(String sender) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(sender);
    }

    public static final EmailConfig DISABLED = new EmailConfig(
            "imap", "", 993, "", 587, "", "", false, 60, new String[]{"INBOX"}, Set.of());
}
