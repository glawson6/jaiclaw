# JaiClaw Channel Modules Reference

[Back to Developer Guide](../JAICLAW-DEVELOPER-GUIDE.md)

---

## Table of Contents

1. [jaiclaw-channel-telegram](#jaiclaw-channel-telegram)
2. [jaiclaw-channel-slack](#jaiclaw-channel-slack)
3. [jaiclaw-channel-discord](#jaiclaw-channel-discord)
4. [jaiclaw-channel-email](#jaiclaw-channel-email)
5. [jaiclaw-channel-sms](#jaiclaw-channel-sms)
6. [jaiclaw-channel-signal](#jaiclaw-channel-signal)
7. [jaiclaw-channel-teams](#jaiclaw-channel-teams)
8. [Channel Adapter Pattern Summary](#channel-adapter-pattern-summary)

---

## jaiclaw-channel-telegram

**Purpose**: Telegram Bot API adapter supporting long polling (local dev) and webhook (production) inbound modes.

**Package**: `io.jaiclaw.channel.telegram`
**Directory**: `channels/jaiclaw-channel-telegram`
**Dependencies**: `jaiclaw-channel-api`, `jaiclaw-config`

### Class Reference

| Class | Type | Description |
|---|---|---|
| TelegramConfig | record | Configuration with polling/webhook modes, user filtering, webhook verification, bot token masking |
| TelegramAdapter | class | Bot API adapter with polling and webhook support; optional secret token verification; pluggable HTTP client and polling strategy |
| TelegramHttpClient | interface | Pluggable HTTP transport for Bot API calls (post, get, downloadFile) |
| DefaultTelegramHttpClient | class | Default implementation using JDK `HttpClient` with proxy support via `ProxyAwareHttpClientFactory` |
| TelegramPollingStrategy | interface | Pluggable polling loop for `getUpdates` replacement |
| CamelTelegramPollingStrategy | class | Apache Camel-based polling using `camel-telegram` component |
| SendTelegramTool | class | Reusable tool that sends a message to a specific Telegram chat ID via the ChannelRegistry |
| TelegramUserIdFilter | class | Gateway filter (`GatewayMessageFilter`) enforcing user ID authorization and rate limiting on inbound Telegram messages |

### Class Relationships

```
ChannelAdapter (SPI from jaiclaw-channel-api)
  │
  └── TelegramAdapter
        ├── TelegramConfig (record)
        ├── TelegramHttpClient (pluggable HTTP transport)
        │     └── DefaultTelegramHttpClient (JDK HttpClient + proxy support)
        ├── TelegramPollingStrategy (pluggable polling)
        │     ├── (built-in getUpdates loop — default)
        │     └── CamelTelegramPollingStrategy (Apache Camel)
        └── WebhookDispatcher (for webhook mode)

SendTelegramTool (implements ToolCallback)
  └── ChannelRegistry → TelegramAdapter.sendMessage()

TelegramUserIdFilter (implements GatewayMessageFilter)
  ├── Set<String> allowedUserIds (authorization)
  ├── UserRateLimiter (rate limiting)
  └── GatewayService downstream (via FilteredGatewayLifecycle)
```

### SendTelegramTool

A reusable `ToolCallback` provided by the Telegram channel module. It sends a message to a specific Telegram chat ID by looking up the Telegram adapter from the `ChannelRegistry`. This allows agents and cron jobs to proactively deliver messages to Telegram users.

| Property | Value |
|---|---|
| Tool name | `send_telegram` |
| Profiles | FULL, MINIMAL |
| Parameters | `chat_id` (string, required), `message` (string, required, Markdown supported) |

Wire it as a bean in any app that has the Telegram channel enabled:

```java
@Bean
SendTelegramTool sendTelegramTool(ChannelRegistry channelRegistry) {
    return new SendTelegramTool(channelRegistry);
}
```

### TelegramUserIdFilter (Auto-Configured)

A gateway-level filter that implements `GatewayMessageFilter` and enforces:

1. **User ID authorization** -- only configured user IDs may interact
2. **Rate limiting** -- prevents command flooding per user via `UserRateLimiter` (from `jaiclaw-security`)

This supplements the `allowedUserIds` filtering built into `TelegramAdapter`. The adapter's built-in filter runs first at the polling/webhook level; this filter adds a second layer at the gateway level for defense in depth, plus rate limiting.

**Auto-configuration:** When `jaiclaw.channels.telegram.allowed-users` is set, the framework automatically creates:
- A `UserRateLimiter` bean (configurable via `jaiclaw.channels.telegram.rate-limit`, default: 10 requests/minute)
- A `TelegramUserIdFilter` bean wired to the `GatewayService`
- A `FilteredGatewayLifecycle` (instead of the default `GatewayLifecycle`) that routes all channel messages through the filter before reaching the `GatewayService`

No manual bean definitions are needed — the presence of `allowed-users` is sufficient to activate the full filter chain.

### Dual-Mode

| Mode | Trigger | How It Works |
|---|---|---|
| **Polling** (local dev) | `TELEGRAM_WEBHOOK_URL` is blank | Calls `getUpdates` with 30s long-poll |
| **Webhook** (production) | `TELEGRAM_WEBHOOK_URL` is set | Registers webhook, receives POSTs |

### Configuration

| Variable | Required | Description |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | Yes | Bot token from @BotFather |
| `TELEGRAM_WEBHOOK_URL` | No | Webhook URL (blank = polling mode) |
| `TELEGRAM_ALLOWED_USERS` | No | Comma-separated user IDs for authorization (activates `TelegramUserIdFilter`) |

### Pluggable Transport (0.4.0+)

| Property | Default | Description |
|---|---|---|
| `jaiclaw.channels.telegram.http-client` | `default` | HTTP client for Bot API calls. Provide a `TelegramHttpClient` bean to override. |
| `jaiclaw.channels.telegram.polling-strategy` | `builtin` | Polling implementation. Set to `camel` to use Apache Camel's Telegram component. |

### Security Hardening (Opt-In)

| Property | Default | Description |
|---|---|---|
| `jaiclaw.channels.telegram.verify-webhook` | `false` | Verify `X-Telegram-Bot-Api-Secret-Token` header on inbound webhooks |
| `jaiclaw.channels.telegram.webhook-secret-token` | — | Token sent to Telegram `setWebhook` and verified on inbound |
| `jaiclaw.channels.telegram.mask-bot-token` | `false` | Use SHA-256 hash prefix of bot token as `accountId` in session keys instead of raw token |
| `jaiclaw.channels.telegram.rate-limit` | `10` | Max messages per minute per user (used by auto-configured `UserRateLimiter`) |

When `mask-bot-token` is enabled, `TelegramConfig.accountId()` returns `"tg_" + SHA256(botToken).substring(0,12)` instead of the raw bot token, preventing token leakage in session keys and logs.

---

## jaiclaw-channel-slack

**Purpose**: Slack adapter using Socket Mode WebSocket (local dev) or Events API webhook (production).

**Package**: `io.jaiclaw.channel.slack`
**Directory**: `channels/jaiclaw-channel-slack`
**Dependencies**: `jaiclaw-channel-api`, `jaiclaw-config`

### Class Reference

| Class | Type | Description |
|---|---|---|
| SlackConfig | record | Configuration with Socket Mode/Events API modes, sender filtering, signature verification |
| SlackAdapter | class | Socket Mode WebSocket or Events API webhook adapter; optional HMAC-SHA256 signature verification |

### Class Relationships

```
ChannelAdapter (SPI)
  │
  └── SlackAdapter
        ├── SlackConfig (record)
        ├── WebhookDispatcher (for Events API mode)
        ├── HttpClient WebSocket (for Socket Mode)
        └── RestTemplate (chat.postMessage)
```

### Dual-Mode

| Mode | Trigger | How It Works |
|---|---|---|
| **Socket Mode** (local dev) | `SLACK_APP_TOKEN` is set | WebSocket to `apps.connections.open` |
| **Events API** (production) | `SLACK_APP_TOKEN` is blank | Receives POSTs at `/webhook/slack` |

### Configuration

| Variable | Required | Description |
|---|---|---|
| `SLACK_BOT_TOKEN` | Yes | Bot OAuth token (xoxb-...) |
| `SLACK_APP_TOKEN` | No | App-level token for Socket Mode (xapp-...) |
| `SLACK_SIGNING_SECRET` | Webhook mode | Signing secret for request verification |

### Security Hardening (Opt-In)

| Property | Default | Description |
|---|---|---|
| `jaiclaw.channels.slack.verify-signature` | `false` | Enable HMAC-SHA256 verification of `X-Slack-Signature` header |

When enabled and `signingSecret` is non-blank, verifies `X-Slack-Signature` / `X-Slack-Request-Timestamp` headers using HMAC-SHA256. Rejects requests with missing/invalid/stale (>5 min) signatures. Uses `MessageDigest.isEqual()` for constant-time comparison.

---

## jaiclaw-channel-discord

**Purpose**: Discord adapter using Gateway WebSocket (local dev) or Interactions webhook (production) with heartbeat and IDENTIFY handshake.

**Package**: `io.jaiclaw.channel.discord`
**Directory**: `channels/jaiclaw-channel-discord`
**Dependencies**: `jaiclaw-channel-api`, `jaiclaw-config`

### Class Reference

| Class | Type | Description |
|---|---|---|
| DiscordConfig | record | Configuration with Gateway/Interactions modes and sender filtering |
| DiscordAdapter | class | Gateway WebSocket or Interactions webhook adapter |

### Class Relationships

```
ChannelAdapter (SPI)
  │
  └── DiscordAdapter
        ├── DiscordConfig (record)
        ├── WebhookDispatcher (for Interactions mode)
        ├── HttpClient WebSocket (for Gateway mode)
        │     ├── HELLO → heartbeat interval
        │     ├── IDENTIFY → bot token + intents
        │     └── MESSAGE_CREATE → inbound messages
        └── RestTemplate (channels/{id}/messages)
```

### Dual-Mode

| Mode | Trigger | How It Works |
|---|---|---|
| **Gateway** (local dev) | `DISCORD_USE_GATEWAY=true` | WebSocket to Gateway with heartbeat |
| **Interactions** (production) | `DISCORD_USE_GATEWAY` unset | Receives POSTs at `/webhook/discord` |

### Configuration

| Variable | Required | Description |
|---|---|---|
| `DISCORD_BOT_TOKEN` | Yes | Bot token |
| `DISCORD_USE_GATEWAY` | No | `true` for Gateway WebSocket mode |
| `DISCORD_APPLICATION_ID` | Webhook mode | Application ID for Interactions |

---

## jaiclaw-channel-email

**Purpose**: Email adapter with IMAP inbound polling and SMTP outbound, supporting MIME multipart messages and file attachments.

**Package**: `io.jaiclaw.channel.email`
**Directory**: `channels/jaiclaw-channel-email`
**Dependencies**: `jaiclaw-channel-api`, `jaiclaw-config`, Jakarta Mail

### Class Reference

| Class | Type | Description |
|---|---|---|
| EmailConfig | record | Configuration with IMAP/SMTP settings, polling interval, sender filtering |
| EmailAdapter | class | IMAP polling + SMTP outbound with MIME multipart support |

### Class Relationships

```
ChannelAdapter (SPI)
  │
  └── EmailAdapter
        ├── EmailConfig (record)
        ├── Jakarta Mail IMAP (polling on virtual thread)
        │     ├── polls INBOX at configurable interval
        │     ├── marks processed as SEEN
        │     └── parses MIME multipart → attachments
        └── Jakarta Mail SMTP (STARTTLS outbound)
```

### Mode

Always polling-based (no webhook mode). No public endpoint required.

### Configuration

| Variable | Required | Description |
|---|---|---|
| `EMAIL_IMAP_HOST` | Yes | IMAP server hostname |
| `EMAIL_SMTP_HOST` | Yes | SMTP server hostname |
| `EMAIL_USERNAME` | Yes | Account username |
| `EMAIL_PASSWORD` | Yes | Password or app password |
| `EMAIL_PROVIDER` | No | `imap` (default), `gmail`, `outlook` |
| `EMAIL_IMAP_PORT` | No | Default: 993 |
| `EMAIL_SMTP_PORT` | No | Default: 587 |
| `EMAIL_POLL_INTERVAL` | No | Seconds (default: 60) |

---

## jaiclaw-channel-sms

**Purpose**: SMS/MMS adapter using Twilio REST API for outbound and webhook for inbound messages.

**Package**: `io.jaiclaw.channel.sms`
**Directory**: `channels/jaiclaw-channel-sms`
**Dependencies**: `jaiclaw-channel-api`, `jaiclaw-config`

### Class Reference

| Class | Type | Description |
|---|---|---|
| SmsConfig | record | Configuration with Twilio credentials and sender filtering |
| SmsAdapter | class | Twilio Messages API outbound, webhook inbound |

### Class Relationships

```
ChannelAdapter (SPI)
  │
  └── SmsAdapter
        ├── SmsConfig (record)
        ├── RestTemplate (Twilio Messages API, Basic auth)
        └── WebhookDispatcher
              └── /webhooks/sms (form-encoded POST from Twilio)
                    ├── From, Body, MessageSid
                    └── NumMedia, MediaUrl0 (MMS)
```

### Mode

Always webhook-based. Requires ngrok for local development.

### Configuration

| Variable | Required | Description |
|---|---|---|
| `TWILIO_ACCOUNT_SID` | Yes | Account SID |
| `TWILIO_AUTH_TOKEN` | Yes | Auth Token |
| `TWILIO_FROM_NUMBER` | Yes | Phone number for outbound |

---

## jaiclaw-channel-signal

**Purpose**: Signal adapter supporting EMBEDDED mode (signal-cli daemon with JSON-RPC) and HTTP_CLIENT mode (REST sidecar polling).

**Package**: `io.jaiclaw.channel.signal`
**Directory**: `channels/jaiclaw-channel-signal`
**Dependencies**: `jaiclaw-channel-api`, `jaiclaw-config`

### Class Reference

| Class | Type | Description |
|---|---|---|
| SignalConfig | record | Configuration for EMBEDDED and HTTP_CLIENT modes |
| SignalMode | enum | EMBEDDED or HTTP_CLIENT |
| SignalAdapter | class | Dual-mode adapter using CliProcessBridge or REST polling |

### Class Relationships

```
ChannelAdapter (SPI)
  │
  └── SignalAdapter
        ├── SignalConfig → SignalMode
        │
        ├── EMBEDDED mode:
        │     └── CliProcessBridge (from jaiclaw-channel-api)
        │           └── JsonRpcClient (JSON-RPC 2.0 over TCP)
        │                 └── signal-cli daemon process
        │
        └── HTTP_CLIENT mode:
              └── RestTemplate
                    └── signal-cli-rest-api sidecar (polling)
```

### Dual-Mode

| Mode | How It Works |
|---|---|
| **EMBEDDED** | Manages signal-cli daemon via ProcessBuilder + JSON-RPC |
| **HTTP_CLIENT** | Polls signal-cli-rest-api sidecar via REST |

---

## jaiclaw-channel-teams

**Purpose**: Microsoft Teams adapter using Bot Framework REST API with Azure AD OAuth 2.0 for outbound and JWT-validated webhook for inbound.

**Package**: `io.jaiclaw.channel.teams`
**Directory**: `channels/jaiclaw-channel-teams`
**Dependencies**: `jaiclaw-channel-api`, `jaiclaw-config`

### Class Reference

| Class | Type | Description |
|---|---|---|
| TeamsConfig | record | Azure AD credentials, JWT validation, sender filtering |
| TeamsTokenManager | class | Azure AD OAuth 2.0 token refresh with 5-min margin |
| TeamsJwtValidator | class | Validates inbound JWT from Bot Framework using RSA/JWKS |
| TeamsAdapter | class | Bot Framework adapter with OAuth outbound, JWT-validated inbound |

### Class Relationships

```
ChannelAdapter (SPI)
  │
  └── TeamsAdapter
        ├── TeamsConfig (record)
        ├── TeamsTokenManager
        │     └── Azure AD OAuth 2.0 (client_credentials grant)
        │           └── ReentrantLock (thread-safe token caching)
        ├── TeamsJwtValidator
        │     └── JWKS key caching (24h)
        │           └── JDK-native RSA verification (zero external deps)
        ├── WebhookDispatcher (inbound activities)
        └── RestTemplate (Bot Framework REST API)
```

### Mode

Webhook-based only. Azure AD app registration required.

### Configuration

| Variable | Required | Description |
|---|---|---|
| `TEAMS_APP_ID` | Yes | Azure AD application ID |
| `TEAMS_APP_PASSWORD` | Yes | Azure AD client secret |
| `TEAMS_TENANT_ID` | No | Azure AD tenant ID |

---

## Channel Adapter Pattern Summary

### Consistent Structure

Every channel module follows the same pattern:

```
Config record + Adapter class → implements ChannelAdapter
```

| Module | Config | Adapter | Extra Classes |
|---|---|---|---|
| jaiclaw-channel-telegram | TelegramConfig | TelegramAdapter | SendTelegramTool, TelegramUserIdFilter, TelegramHttpClient, DefaultTelegramHttpClient, TelegramPollingStrategy, CamelTelegramPollingStrategy |
| jaiclaw-channel-slack | SlackConfig | SlackAdapter | — |
| jaiclaw-channel-discord | DiscordConfig | DiscordAdapter | — |
| jaiclaw-channel-email | EmailConfig | EmailAdapter | — |
| jaiclaw-channel-sms | SmsConfig | SmsAdapter | — |
| jaiclaw-channel-signal | SignalConfig | SignalAdapter | SignalMode (enum) |
| jaiclaw-channel-teams | TeamsConfig | TeamsAdapter | TeamsTokenManager, TeamsJwtValidator |

### Dual-Mode Principle

**Inbound** varies by mode (polling vs webhook), **outbound** is always the same:

```
Inbound:
  Local dev → adapter initiates connection (polling/WebSocket)
  Production → platform pushes events to webhook endpoint

Outbound:
  Always the same REST API call regardless of inbound mode
```

### No Public Endpoint Needed (Local Dev)

| Channel | Method |
|---|---|
| Telegram | Long polling (`getUpdates`) |
| Slack | Socket Mode (WebSocket) |
| Discord | Gateway WebSocket |
| Email | IMAP polling |
| Signal | CliProcessBridge or REST sidecar |

SMS (Twilio) always requires a webhook endpoint (use ngrok for local dev).
