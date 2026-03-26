# Identity Linking

Module: `jaiclaw-identity`

## Overview

Links user identities across channels so the agent recognizes that the same person on Slack, Telegram, and Discord is one user. Uses a canonical user ID that maps to channel-specific user IDs.

## Usage

```java
// Create the service
IdentityLinkStore store = new IdentityLinkStore(Path.of("data/identity-links.json"));
IdentityLinkService service = new IdentityLinkService(store);

// Link a Telegram user to a canonical ID (auto-generates canonical ID if null)
IdentityLink link1 = service.link(null, "telegram", "12345");
String canonicalId = link1.canonicalUserId(); // e.g., "a1b2c3d4-..."

// Link a Slack user to the same canonical ID
IdentityLink link2 = service.link(canonicalId, "slack", "U98765");

// Now both channel users resolve to the same canonical ID
IdentityResolver resolver = new IdentityResolver(store);
String resolved1 = resolver.resolve("telegram", "12345");  // → canonicalId
String resolved2 = resolver.resolve("slack", "U98765");    // → canonicalId (same)

// Unknown users return their channel-specific ID
String unknown = resolver.resolve("discord", "999");        // → "999"

// Unlink
service.unlink("telegram", "12345");
```

## Key Classes

| Class | Purpose |
|-------|---------|
| `IdentityLinkService` | Link/unlink operations, auto UUID generation |
| `IdentityLinkStore` | JSON file persistence, ConcurrentHashMap for fast lookup |
| `IdentityResolver` | Resolves channel+userId → canonical user ID |
| `IdentityLink` | Record: canonicalUserId, channel, channelUserId (in `jaiclaw-core`) |

## Storage

Links persist to a JSON file (e.g., `data/identity-links.json`):

```json
[
  {"canonicalUserId": "a1b2c3d4-...", "channel": "telegram", "channelUserId": "12345"},
  {"canonicalUserId": "a1b2c3d4-...", "channel": "slack", "channelUserId": "U98765"}
]
```

The store uses a `ConcurrentHashMap` keyed by `channel:channelUserId` for O(1) lookups.

## Integration

When a message arrives on any channel, use `IdentityResolver.resolve(channel, userId)` to get the canonical ID. This canonical ID can then be used for:

- Cross-channel session continuity
- Unified user preferences
- Consistent audit trails
- Per-user rate limiting across all channels
