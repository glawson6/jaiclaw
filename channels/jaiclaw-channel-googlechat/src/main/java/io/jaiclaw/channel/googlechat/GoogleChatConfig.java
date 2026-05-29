package io.jaiclaw.channel.googlechat;

import java.util.Set;

/**
 * Configuration for the Google Chat channel adapter.
 *
 * @param projectId             Google Cloud project ID
 * @param serviceAccountKeyPath path to the service account JSON key file
 * @param webhookPath           HTTP path for Pub/Sub push subscriptions
 * @param enabled               whether the adapter is enabled
 * @param allowedSenderIds      Google Chat user IDs allowed to send messages (empty = allow all)
 */
public record GoogleChatConfig(
        String projectId,
        String serviceAccountKeyPath,
        String webhookPath,
        boolean enabled,
        Set<String> allowedSenderIds
) {
    public GoogleChatConfig {
        if (projectId == null) projectId = "";
        if (serviceAccountKeyPath == null) serviceAccountKeyPath = "";
        if (webhookPath == null || webhookPath.isBlank()) webhookPath = "/webhooks/googlechat";
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    public boolean isSenderAllowed(String senderId) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(senderId);
    }

    public static final GoogleChatConfig DISABLED = new GoogleChatConfig(
            "", "", "/webhooks/googlechat", false, Set.of()
    );

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String projectId;
        private String serviceAccountKeyPath;
        private String webhookPath;
        private boolean enabled;
        private Set<String> allowedSenderIds;

        public Builder projectId(String projectId) { this.projectId = projectId; return this; }
        public Builder serviceAccountKeyPath(String serviceAccountKeyPath) { this.serviceAccountKeyPath = serviceAccountKeyPath; return this; }
        public Builder webhookPath(String webhookPath) { this.webhookPath = webhookPath; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder allowedSenderIds(Set<String> allowedSenderIds) { this.allowedSenderIds = allowedSenderIds; return this; }

        public GoogleChatConfig build() {
            return new GoogleChatConfig(projectId, serviceAccountKeyPath, webhookPath, enabled, allowedSenderIds);
        }
    }
}
