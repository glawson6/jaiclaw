package io.jaiclaw.subscription.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Wraps Telegram Bot API admin operations for managing group/channel membership.
 */
public class TelegramGroupManager {

    private static final Logger log = LoggerFactory.getLogger(TelegramGroupManager.class);
    private static final String API_BASE = "https://api.telegram.org/bot";

    private final String botToken;
    private final RestTemplate restTemplate;

    public TelegramGroupManager(String botToken) {
        this(botToken, new RestTemplate());
    }

    public TelegramGroupManager(String botToken, RestTemplate restTemplate) {
        this.botToken = botToken;
        this.restTemplate = restTemplate;
    }

    /**
     * Create a one-time invite link for a chat (channel or group).
     * Uses member_limit=1 so the link can only be used once.
     *
     * @return the invite link URL, or null on failure
     */
    public String createInviteLink(String chatId) {
        try {
            var body = Map.of(
                    "chat_id", chatId,
                    "member_limit", 1,
                    "creates_join_request", false
            );
            var response = restTemplate.postForEntity(
                    apiUrl("createChatInviteLink"), body, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String link = response.getBody().path("result").path("invite_link").asText();
                log.info("Created invite link for chat {}", chatId);
                return link;
            }
        } catch (Exception e) {
            log.error("Failed to create invite link for {}: {}", chatId, e.getMessage());
        }
        return null;
    }

    /**
     * Ban a user from a chat (removes them). Then immediately unban
     * so they aren't permanently blocked.
     */
    public boolean removeUser(String chatId, String userId) {
        try {
            // Ban (kicks the user)
            var banBody = Map.of("chat_id", chatId, "user_id", Long.parseLong(userId));
            restTemplate.postForEntity(apiUrl("banChatMember"), banBody, JsonNode.class);

            // Immediately unban so they can rejoin with a new invite
            var unbanBody = Map.of(
                    "chat_id", chatId,
                    "user_id", Long.parseLong(userId),
                    "only_if_banned", true
            );
            restTemplate.postForEntity(apiUrl("unbanChatMember"), unbanBody, JsonNode.class);

            log.info("Removed user {} from chat {}", userId, chatId);
            return true;
        } catch (Exception e) {
            log.error("Failed to remove user {} from {}: {}", userId, chatId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if a user is a member of a chat.
     *
     * @return true if the user is a member, administrator, or creator
     */
    public boolean isMember(String chatId, String userId) {
        try {
            var body = Map.of("chat_id", chatId, "user_id", Long.parseLong(userId));
            var response = restTemplate.postForEntity(
                    apiUrl("getChatMember"), body, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String status = response.getBody().path("result").path("status").asText();
                return "member".equals(status) || "administrator".equals(status) || "creator".equals(status);
            }
        } catch (Exception e) {
            log.warn("Failed to check membership for user {} in {}: {}", userId, chatId, e.getMessage());
        }
        return false;
    }

    /**
     * Send a text message to a chat.
     */
    public boolean sendMessage(String chatId, String text) {
        try {
            var body = Map.of(
                    "chat_id", chatId,
                    "text", text,
                    "parse_mode", "Markdown"
            );
            var response = restTemplate.postForEntity(apiUrl("sendMessage"), body, JsonNode.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    /**
     * Send an invoice for Telegram Payments.
     */
    public boolean sendInvoice(String chatId, String title, String description,
                               String payload, String providerToken,
                               String currency, int amount) {
        try {
            var body = Map.of(
                    "chat_id", chatId,
                    "title", title,
                    "description", description,
                    "payload", payload,
                    "provider_token", providerToken,
                    "currency", currency,
                    "prices", new Object[]{Map.of("label", title, "amount", amount)}
            );
            var response = restTemplate.postForEntity(apiUrl("sendInvoice"), body, JsonNode.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to send invoice to {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    private String apiUrl(String method) {
        return API_BASE + botToken + "/" + method;
    }
}
