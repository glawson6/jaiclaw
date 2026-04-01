# Voice Call Demo

Telephony demo — outbound appointment reminder calls and inbound customer service via Twilio.

## What This Demonstrates

- **jaiclaw-voice-call** extension — Twilio telephony, webhook handling, WebSocket media streaming, MCP tools
- **MCP tool-driven call control** — the agent initiates calls, speaks, listens, and hangs up via tool calls
- **Custom domain tools** — appointment lookup, rescheduling, and customer lookup tools the agent uses during calls
- **Voice-specific skill** — teaches the agent conversational phone etiquette and call workflows
- **Inbound call handling** — accepts incoming calls and routes them to the agent

## Architecture

```
                                  ┌─────────────────────────┐
                                  │   Voice Call Demo App    │
                                  │  (Spring Boot, port 8080)│
                                  └────────┬────────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
           ┌────────┴────────┐   ┌────────┴────────┐   ┌────────┴────────┐
           │  Webhook        │   │  Media Stream   │   │  MCP Tools      │
           │  /voice/webhook │   │  /voice/media-  │   │  voice_call_*   │
           │  (Twilio POST)  │   │  stream (WSS)   │   │  + domain tools │
           └────────┬────────┘   └────────┬────────┘   └────────┬────────┘
                    │                      │                      │
                    └──────────────────────┼──────────────────────┘
                                           │
                                  ┌────────┴────────┐
                                  │   CallManager   │
                                  │  (state machine │
                                  │   + transcripts)│
                                  └────────┬────────┘
                                           │
                                  ┌────────┴────────┐
                                  │  Twilio Cloud   │
                                  │  (PSTN gateway) │
                                  └─────────────────┘
```

## Prerequisites

- Java 21+
- A [Twilio account](https://www.twilio.com/try-twilio) with:
  - Account SID and Auth Token (from [Console Dashboard](https://console.twilio.com/))
  - A phone number with Voice capability
- A publicly reachable URL (for Twilio webhooks)
- Anthropic API key

## Quick Start

### 1. Get a public URL

For local development, use [ngrok](https://ngrok.com/):

```bash
ngrok http 8080
# Note the https://xxxx.ngrok.io URL
```

### 2. Configure Twilio

In the [Twilio Console](https://console.twilio.com/), go to your phone number's configuration:
- **Voice Configuration > A call comes in**: Webhook
- **URL**: `https://xxxx.ngrok.io/voice/webhook`
- **Method**: POST

### 3. Set environment variables

```bash
export TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_AUTH_TOKEN=your_auth_token
export TWILIO_PHONE_NUMBER=+15551234567     # Your Twilio number
export PUBLIC_URL=https://xxxx.ngrok.io      # From step 1
export ANTHROPIC_API_KEY=sk-ant-...
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ../../mvnw spring-boot:run
```


### 4. Build and run

```bash
cd jaiclaw-examples/voice-call-demo
JAVA_HOME=$HOME/.sdkman/candidates/java/current \
  ../../mvnw spring-boot:run
```

### 5. Test it

**Trigger an outbound reminder call** via the REST API:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "Call +15559876543 to remind them about their appointment tomorrow"}'
```

**Receive an inbound call** by dialing your Twilio phone number from any phone.

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `TWILIO_ACCOUNT_SID` | Yes | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | Yes | Twilio Auth Token |
| `TWILIO_PHONE_NUMBER` | Yes | Your Twilio phone number (E.164) |
| `PUBLIC_URL` | Yes | Publicly reachable URL for webhooks |
| `ANTHROPIC_API_KEY` | Yes | Anthropic API key |
| `OPENAI_API_KEY` | No | For real-time STT via OpenAI Realtime |
| `INBOUND_POLICY` | No | `open` (default for demo), `disabled`, or `allowlist` |

## Custom Tools

The demo registers three domain-specific tools:

| Tool | Description |
|------|-------------|
| `lookup_appointments` | Find upcoming appointments by phone number |
| `reschedule_appointment` | Reschedule an appointment to a new date/time |
| `lookup_customer` | Look up customer info by phone number |

These are simulated — in production, replace with real backend integrations.

## Call Flow Example

```
Agent: "Hello Jane! This is a reminder that you have an appointment
        tomorrow at two thirty PM with Doctor Rodriguez at the
        Main Street Clinic. Can you confirm you'll be there?"

Jane:  "Actually, can I reschedule to Friday?"

Agent: "Of course! What time works best for you on Friday?"

Jane:  "How about 10 AM?"

Agent: "I've rescheduled your appointment with Doctor Rodriguez
        to Friday at ten AM. Is there anything else I can help with?"

Jane:  "No, that's all. Thank you!"

Agent: "You're welcome, Jane. Have a great day! Goodbye."
       [call ends]
```

## Related

- [Voice Call Extension docs](../../docs/features/VOICE-CALL.md) — full ops guide and configuration reference
- [Voice (TTS/STT) docs](../../docs/features/VOICE.md) — underlying TTS/STT provider layer
- [Skill file](src/main/resources/skills/voice-call-demo.md) — agent behavior instructions
