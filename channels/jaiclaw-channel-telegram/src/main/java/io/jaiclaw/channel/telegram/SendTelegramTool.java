package io.jaiclaw.channel.telegram;

import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.channel.DeliveryResult;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reusable tool that sends a message to a specific Telegram chat ID via the Telegram channel adapter.
 * Can be wired as a bean in any app that has the Telegram channel enabled.
 */
public class SendTelegramTool implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(SendTelegramTool.class);

    private final ChannelRegistry channelRegistry;

    public SendTelegramTool(ChannelRegistry channelRegistry) {
        this.channelRegistry = channelRegistry;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "send_telegram",
                "Send a message to a Telegram chat. Use this to deliver reports to users.",
                "Telegram",
                """
                {
                  "type": "object",
                  "properties": {
                    "chat_id": {
                      "type": "string",
                      "description": "Telegram chat ID to send the message to"
                    },
                    "message": {
                      "type": "string",
                      "description": "The message text to send (Markdown supported)"
                    }
                  },
                  "required": ["chat_id", "message"]
                }
                """,
                Set.of(ToolProfile.FULL, ToolProfile.MINIMAL)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String chatId = (String) parameters.get("chat_id");
        String message = (String) parameters.get("message");

        if (chatId == null || chatId.isBlank()) {
            return new ToolResult.Error("chat_id is required");
        }
        if (message == null || message.isBlank()) {
            return new ToolResult.Error("message is required");
        }

        return channelRegistry.get("telegram")
                .map(adapter -> {
                    ChannelMessage outbound = ChannelMessage.outbound(
                            UUID.randomUUID().toString(),
                            "telegram",
                            "",       // accountId not needed for outbound
                            chatId,
                            message
                    );

                    DeliveryResult result = adapter.sendMessage(outbound);

                    if (result instanceof DeliveryResult.Success success) {
                        log.info("Delivered message to Telegram chat {}", chatId);
                        return (ToolResult) new ToolResult.Success(
                                "Message sent to Telegram chat " + chatId);
                    } else if (result instanceof DeliveryResult.Failure failure) {
                        log.error("Failed to deliver to Telegram chat {}: {}",
                                chatId, failure.message());
                        return (ToolResult) new ToolResult.Error(
                                "Failed to send: " + failure.message());
                    }
                    return (ToolResult) new ToolResult.Error("Unknown delivery result");
                })
                .orElse(new ToolResult.Error(
                        "Telegram channel adapter not available. "
                                + "Ensure TELEGRAM_BOT_TOKEN is set and telegram is enabled."));
    }
}
