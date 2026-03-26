# Meeting Assistant Example

Meeting transcription via STT, cross-channel identity linking, and summary delivery to Slack.

## What This Demonstrates

- **Voice** module for speech-to-text transcription
- **Identity** module for cross-channel participant linking
- **Slack** channel adapter for summary delivery
- Custom **ToolCallback** implementations (TranscriptionTool, SummaryTool)

## Architecture

Where this example fits in JaiClaw:

```
┌───────────────────────────────────────────────────────────┐
│                   MEETING ASSISTANT APP                     │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Channels         │  [jaiclaw-channel-slack] → summary post   │
├──────────────────┼────────────────────────────────────────┤
│ Agent Runtime    │  AgentRuntime → LLM → Tools             │
├──────────────────┼────────────────────────────────────────┤
│ Voice            │  [jaiclaw-voice] → speech-to-text         │
├──────────────────┼────────────────────────────────────────┤
│ Identity         │  [jaiclaw-identity] → participant linking │
├──────────────────┼────────────────────────────────────────┤
│ Custom Tools     │  [TranscriptionTool] [SummaryTool]      │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  Audio ──► Voice (STT) ──► transcript
                                │
                      Identity module
                      (link speakers to
                       Slack profiles)
                                │
                                ▼
                          AgentRuntime
                                │
                       ┌────────┼────────┐
                       ▼        ▼        ▼
                 SummaryTool   LLM   Identity
                       │        │        │
                       └────────┼────────┘
                                ▼
                       Meeting summary
                                │
                                ▼
                         Slack channel
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI/Gemini API key OR Ollama running locally
- Slack app (optional — for Slack delivery)

## Configuration

| Variable | Description |
|----------|-------------|
| `ANTHROPIC_API_KEY` | Anthropic API key (or use `OPENAI_API_KEY` / Ollama) |
| `SLACK_BOT_TOKEN` | Slack bot token (optional) |
| `SLACK_APP_TOKEN` | Slack app token for Socket Mode (optional) |

## Build & Run

```bash
cd jaiclaw-examples/meeting-assistant
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Testing It

```bash
# Process a meeting recording
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Transcribe and summarize the meeting at https://example.com/meetings/q3-review.mp3"}'
```
