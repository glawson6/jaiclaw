# Voice Call — Telephony

Module: `jaiclaw-voice-call`

## Overview

Adds telephony support (phone calls) to JaiClaw via Twilio. The agent can make and receive phone calls, speak to users with TTS, transcribe user speech in real-time, and hold multi-turn voice conversations — all controllable through MCP tools.

Built on top of `jaiclaw-voice` (TTS/STT providers) and follows the same extension patterns as `jaiclaw-discord-tools` and `jaiclaw-slack-tools`.

## Prerequisites

- A Twilio account with:
  - Account SID and Auth Token
  - A purchased phone number (E.164 format, e.g. `+15551234567`)
  - Voice capabilities enabled on the number
- A publicly accessible URL for webhooks (Twilio must be able to reach your JaiClaw instance)
- OpenAI API key (for real-time STT via the Realtime API, optional — falls back to Twilio's built-in `<Gather>`)

## Setup

### 1. Add the dependency

If using the Spring Boot starter, `jaiclaw-voice-call` is included automatically. Otherwise add it to your `pom.xml`:

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-voice-call</artifactId>
</dependency>
```

### 2. Configure `application.yml`

```yaml
jaiclaw:
  voice-call:
    enabled: true
    provider: twilio

    twilio:
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}

    serve:
      webhook-path: /voice/webhook
      public-url: https://your-domain.com    # Must be reachable by Twilio

    outbound:
      from-number: "+15551234567"            # Your Twilio phone number
      default-mode: CONVERSATION             # CONVERSATION or NOTIFY
      max-duration-sec: 600                  # Max call duration (10 min default)
      silence-timeout-sec: 30               # Hang up after 30s silence

    inbound:
      policy: disabled                       # disabled | open | allowlist
      greeting: "Hello, how can I help you?"
      voice: Polly.Amy
      allowed-from:                          # Used when policy=allowlist
        - "+15559876543"

    streaming:
      stt-model: gpt-4o-transcribe           # OpenAI Realtime model
      openai-api-key: ${OPENAI_API_KEY}      # Falls back to main key
      vad-silence-duration-ms: 800
      vad-threshold: 0.5
      pre-start-timeout-sec: 30
```

### 3. Configure Twilio webhook

In the Twilio Console, set the **Voice webhook URL** for your phone number to:

```
https://your-domain.com/voice/webhook
```

Method: **POST**

This is where Twilio sends call status events and speech recognition results.

### 4. Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `TWILIO_ACCOUNT_SID` | Yes | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | Yes | Twilio Auth Token |
| `OPENAI_API_KEY` | Optional | For real-time STT via OpenAI Realtime API |

## Architecture

```
┌─────────────┐     webhook POST     ┌──────────────────┐
│   Twilio    │ ──────────────────── │ WebhookController │
│  (cloud)    │                      └────────┬─────────┘
│             │     WebSocket (media)          │
│             │ ──────────────────── ┌─────────┴──────────┐
└─────────────┘                      │ MediaStreamHandler  │
                                     └─────────┬──────────┘
                                               │
                                     ┌─────────┴──────────┐
                                     │    CallManager      │
                                     │  (state machine,    │
                                     │   event processor)  │
                                     └─────────┬──────────┘
                                               │
                              ┌────────────────┼────────────────┐
                              │                │                │
                    ┌─────────┴───┐  ┌────────┴────────┐  ┌───┴──────────┐
                    │  CallStore  │  │ TelephonyProvider│  │ MCP Tools    │
                    │ (JSONL/mem) │  │   (Twilio SPI)   │  │ (5 tools)    │
                    └─────────────┘  └─────────────────┘  └──────────────┘
```

### Key components

| Component | Responsibility |
|-----------|---------------|
| `TelephonyProvider` | SPI for telephony providers (Twilio implemented) |
| `CallManager` | Thread-safe call lifecycle, timers, transcript waiters |
| `CallEventProcessor` | Event deduplication, state transitions, auto-registration |
| `CallLifecycle` | State machine validation |
| `WebhookController` | Spring `@RestController` receiving Twilio webhooks |
| `MediaStreamHandler` | WebSocket handler for bidirectional Twilio media streams |
| `VoiceCallMcpToolProvider` | 5 MCP tools for agent-driven call control |
| `CallStore` | Persistence (in-memory default, JSONL available) |

## MCP Tools

The extension registers an MCP server named `voice-call` with 5 tools:

### `voice_call_initiate`

Initiate an outbound phone call.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `to` | string | Yes | Phone number in E.164 format |
| `message` | string | No | Message to speak when call connects |
| `mode` | string | No | `conversation` (default) or `notify` |

### `voice_call_continue`

Speak a message and wait for the user's response. Returns transcribed speech.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `callId` | string | Yes | Active call ID |
| `message` | string | Yes | Message to speak before listening |

### `voice_call_speak`

Speak a message without waiting for a response.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `callId` | string | Yes | Active call ID |
| `message` | string | Yes | Message to speak |

### `voice_call_end`

End an active call.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `callId` | string | Yes | Call ID to end |

### `voice_call_status`

Get call status, direction, state, and full transcript.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `callId` | string | Yes | Call ID to query |

## Call Modes

| Mode | Behavior |
|------|----------|
| **NOTIFY** | Deliver a one-way message, then automatically hang up. Use for reminders, alerts, and notifications. |
| **CONVERSATION** | Interactive multi-turn voice call. The bot speaks, listens for user speech, and can respond. Use for customer service, surveys, and interactive tasks. |

## Call States

| State | Terminal | Description |
|-------|----------|-------------|
| `INITIATED` | No | Call request sent to provider |
| `RINGING` | No | Phone is ringing |
| `ANSWERED` | No | Call was answered |
| `ACTIVE` | No | Call is active (media stream connected) |
| `SPEAKING` | No | Bot is speaking |
| `LISTENING` | No | Bot is listening for user speech |
| `COMPLETED` | Yes | Call ended normally |
| `HANGUP_USER` | Yes | User hung up |
| `HANGUP_BOT` | Yes | Bot hung up |
| `TIMEOUT` | Yes | Call timed out (max duration or silence) |
| `ERROR` | Yes | Call error |
| `FAILED` | Yes | Call failed to connect |
| `NO_ANSWER` | Yes | No answer |
| `BUSY` | Yes | Busy signal |
| `VOICEMAIL` | Yes | Went to voicemail |

## Inbound Call Policies

| Policy | Behavior |
|--------|----------|
| `disabled` | Reject all inbound calls (default) |
| `open` | Accept all inbound calls |
| `allowlist` | Accept only from numbers listed in `allowed-from` |

## Webhook Security

Twilio webhook requests are verified using HMAC-SHA1 signature validation (`X-Twilio-Signature` header). The verifier supports reverse-proxy URL reconstruction via `X-Forwarded-Proto` and `X-Forwarded-Host` headers for deployments behind nginx, Caddy, or cloud load balancers.

## Media Streaming

For conversation-mode calls, JaiClaw uses Twilio's bidirectional media streams via WebSocket. When a call is answered:

1. TwiML responds with `<Connect><Stream>` pointing to `/voice/media-stream`
2. Twilio opens a WebSocket and streams mu-law (G.711) audio
3. JaiClaw can send audio back for TTS playback
4. Optional: Audio is forwarded to OpenAI Realtime API for real-time STT

Stream authentication uses per-call tokens passed as custom parameters in the TwiML.

## Persistence

| Store | Usage |
|-------|-------|
| `InMemoryCallStore` | Default. Fast, lost on restart. Good for development. |
| `JsonlCallStore` | JSONL append-only file. Survives restarts. Configure by providing a custom `CallStore` bean. |

To use JSONL persistence, register a custom bean:

```java
@Bean
public CallStore callStore() {
    return new JsonlCallStore(Path.of("/data/jaiclaw/calls.jsonl"));
}
```

## Stale Call Reaper

A scheduled task runs every 60 seconds to clean up calls that have exceeded `2x maxDurationSec` without receiving a terminal event from Twilio. This handles edge cases where Twilio webhook delivery fails.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Webhook returns 403 | Check that `twilio.auth-token` matches your Twilio account. Check `X-Forwarded-*` headers if behind a proxy. |
| Call initiates but no audio | Verify `serve.public-url` is reachable from the internet. Twilio must reach your webhook and WebSocket endpoints. |
| STT not working | Check OpenAI API key for real-time STT. Falls back to Twilio `<Gather>` which has higher latency. |
| Calls hang after speaking | Ensure `serve.public-url` includes the correct scheme (`https://`). WebSocket URL is derived from it. |
| "No fromNumber configured" error | Set `outbound.from-number` to your Twilio phone number in E.164 format. |
| Inbound calls rejected | Check `inbound.policy` — default is `disabled`. Set to `open` or `allowlist`. |

## Related

- [Voice (TTS/STT)](VOICE.md) — the underlying TTS/STT provider layer
- Skill file: `skills/voice-call/SKILL.md` — MCP tool descriptions with examples
