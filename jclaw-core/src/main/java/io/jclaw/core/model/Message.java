package io.jclaw.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * Sealed interface representing all message types in a conversation.
 */
public sealed interface Message
        permits UserMessage, AssistantMessage, SystemMessage, ToolResultMessage {

    String id();

    Instant timestamp();

    String content();

    Map<String, Object> metadata();
}
