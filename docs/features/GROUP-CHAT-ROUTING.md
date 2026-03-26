# Group Chat Routing

Module: `jaiclaw-gateway` (enhanced)

## Overview

Adds intelligent message routing for group chats. Agents can be configured to respond only when mentioned, and different agents can be bound to different channels or chat types.

## Components

### RoutingService

Determines whether a message should be processed and which agent should handle it.

```java
RoutingService routing = new RoutingService(bindings, "default-agent", "jaiclaw");

// Check if message should be processed
boolean process = routing.shouldProcess(chatType, channel, peerId, messageText);

// Resolve which agent handles this message
String agentId = routing.resolveAgentId(channel, peerId, chatType);
```

### RoutingBinding

Defines rules for routing messages to agents:

```java
RoutingBinding binding = new RoutingBinding(
    "support-agent",        // agentId
    "slack",                // channel (null = any)
    "#support",             // peerId (null = any)
    ChatType.GROUP,         // chatType (null = any)
    true                    // mentionOnly — only respond when @mentioned
);

// Check if binding matches
boolean matches = binding.matches("slack", "#support", ChatType.GROUP);
```

### MentionParser

Extracts @mentions from messages with channel-specific syntax:

| Channel | Mention Format | Example |
|---------|---------------|---------|
| Slack | `<@USER_ID>` | `<@U123ABC> hello` |
| Discord | `<@USER_ID>` | `<@123456789> hello` |
| Telegram | `@username` or `/cmd@bot` | `@jaiclaw check status` |
| Default | `@name` | `@jaiclaw what's up` |

```java
MentionParser parser = new MentionParser();

Set<String> mentions = parser.extractMentions("slack", "<@U123> can you help?");
// Returns: {"U123"}

boolean mentioned = parser.isMentioned("slack", "<@U123> help", "U123");
// Returns: true
```

## Chat Types

The `ChatType` enum (in `jaiclaw-core`):

| Type | Description |
|------|-------------|
| `DIRECT` | Direct/private message |
| `GROUP` | Group chat or channel |
| `CHANNEL` | Broadcast channel |
| `THREAD` | Thread within a group or channel |

## Routing Logic

1. Find matching `RoutingBinding` for the channel, peerId, and chatType
2. If the binding has `mentionOnly=true`, check if the bot is @mentioned in the message
3. If no binding matches, use the default agent
4. For `DIRECT` messages, always process (no mention required)
