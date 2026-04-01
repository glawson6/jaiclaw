# Travel Planner Example

Multi-step trip planning showcasing **both** JaiClaw plugin tools and Embabel GOAP agent actions, backed by a swappable `TravelDataProvider` SPI.

## What This Demonstrates

- **Embabel GOAP agent** with 5 actions (4 data-gathering + 1 assembly) chained by type dependencies
- **JaiClaw plugin** registering 4 tools (`search_flights`, `search_hotels`, `search_activities`, `get_weather`) via `PluginApi`
- **`TravelDataProvider` SPI** — interface with two implementations swappable via Spring profiles
- **`AbstractBuiltinTool` pattern** for structured tool definitions with JSON Schema input
- **Structured domain records** (`FlightOffer`, `HotelOffer`, `Activity`, `DayForecast`) instead of flat strings
- **LLM only where it adds value** — data comes from the provider; the LLM assembles the itinerary

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                      TRAVEL PLANNER APP                          │
│                    (standalone Spring Boot)                       │
├──────────────────┬───────────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)            │
├──────────────────┼───────────────────────────────────────────────┤
│                  │  ┌─────────────────────────────────────┐      │
│ Two Entry Points │  │ JaiClaw Tool Loop                   │      │
│                  │  │  TravelPlannerPlugin → 4 tools      │      │
│                  │  ├─────────────────────────────────────┤      │
│                  │  │ Embabel GOAP Agent                  │      │
│                  │  │  TravelPlannerAgent → 5 actions     │      │
│                  │  └──────────────┬──────────────────────┘      │
├──────────────────┼─────────────────┼─────────────────────────────┤
│ Data Layer       │     TravelDataProvider (SPI)                   │
│                  │      ├── StubTravelDataProvider (default)      │
│                  │      └── AmadeusApiTravelDataProvider (live)   │
├──────────────────┼───────────────────────────────────────────────┤
│ Domain Records   │  FlightOptions, HotelOptions, ActivityOptions │
│                  │  WeatherForecast, TravelRequest, TripPlan     │
└──────────────────┴───────────────────────────────────────────────┘

Data flow (Embabel GOAP):
  User ──("plan a trip")──► Embabel AgentPlatform
                                  │
                 ┌────────────────┼────────────────┐
                 ▼                ▼                 ▼
          searchFlights    searchHotels    searchActivities
                 │                │                 │
                 │                │                 ▼
                 │                │          ActivityOptions
                 ▼                ▼                 │
          FlightOptions    HotelOptions    checkWeather
                 │                │                 │
                 │                │                 ▼
                 │                │          WeatherForecast
                 └────────────────┼────────────────┘
                                  ▼
                              TripPlan
                          (LLM assembles)

Data flow (JaiClaw Tool Loop):
  User ──("plan a trip")──► LLM ──(tool call)──► search_flights
                                                  search_hotels
                                                  search_activities
                                                  get_weather
                           ◄──(results)──────────┘
                           ──(synthesize)──► final response
```

Both paths delegate to the same `TravelDataProvider` — the stub returns realistic hardcoded data for Tokyo, Paris, Cancun, and NYC.

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI API key

## Build & Run

From the project root (`jaiclaw/`):

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl :jaiclaw-example-travel-planner
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ./mvnw spring-boot:run -pl :jaiclaw-example-travel-planner
```


## Testing It

### Shell (Interactive)

The travel planner includes a Spring Shell CLI with travel-specific commands:

```
shell:>chat "what do you do ?"
I'm Travel Planner, a specialized AI assistant designed to help you plan complete trips!

✈️ Search for flights    🏨 Find hotels
🎯 Discover activities   🌤️ Check weather forecasts
📋 Create complete itineraries with cost breakdown and packing lists

shell:>plan-trip Tokyo --origin JFK --departure 2026-05-01 --return-date 2026-05-08 --budget 5000

shell:>flights Tokyo --origin JFK --departure 2026-05-01 --return-date 2026-05-08

shell:>hotels Tokyo --check-in 2026-05-01 --check-out 2026-05-08

shell:>activities Tokyo --start-date 2026-05-01 --end-date 2026-05-08

shell:>weather Tokyo --start-date 2026-05-01 --end-date 2026-05-08

shell:>new-session
```

The `chat` and `plan-trip` commands use the LLM agent with all travel tools. The direct lookup commands (`flights`, `hotels`, `activities`, `weather`) call the `TravelDataProvider` directly without the LLM.

### REST API

```bash
# Plan a trip (stub data, no external API needed)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Plan a trip to Tokyo for 2 travelers from JFK, departing 2026-05-01, returning 2026-05-08, budget $5000"}'

# Health check
curl http://localhost:8080/api/health
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `GATEWAY_PORT` | `8080` | Server port |
| `ANTHROPIC_API_KEY` | — | Anthropic API key (required) |
| `AI_PROVIDER` | `anthropic` | AI provider (`anthropic`, `openai`, `ollama`) |
| `SPRING_PROFILES_ACTIVE` | — | Set to `live-api` to use Amadeus API |
| `TRAVEL_AMADEUS_API_KEY` | — | Amadeus API key (only with `live-api` profile) |
| `TRAVEL_AMADEUS_API_SECRET` | — | Amadeus API secret (only with `live-api` profile) |

### Bundled Skills

By default, JaiClaw loads **all bundled skills** into the system prompt (`jaiclaw.skills.allow-bundled: ["*"]`). The bundled skill library includes 59 skills totaling ~160KB of text. On a typical developer machine, roughly 27 pass eligibility checks and are injected verbatim into every LLM request — adding ~26,000 tokens of irrelevant context.

**This travel planner disables all bundled skills** because it only needs its 4 custom travel tools:

```yaml
jaiclaw:
  skills:
    allow-bundled: []    # no bundled skills — travel planner uses its own tools
```

Without this setting, a simple "plan a trip to Tokyo" request would consume ~33,000 input tokens (mostly skill content about git, kubectl, shell commands, etc.) instead of ~500. At typical API pricing, that is a **60x cost increase per request** with no benefit.

**If you build your own example or application**, always configure `allow-bundled` explicitly:

```yaml
jaiclaw:
  skills:
    # Option 1: disable all bundled skills
    allow-bundled: []

    # Option 2: whitelist only the skills you need
    allow-bundled:
      - conversation
      - web-research
```

See the [Skills Configuration](../../docs/OPERATIONS.md#skills-configuration) section in the Operations Guide for the full reference.

### Token Usage Logging

The travel planner includes logging configuration to monitor LLM token consumption:

```yaml
logging:
  level:
    # Token count summary after every LLM call (INFO = on, WARN = off)
    io.jaiclaw.agent.AgentRuntime: INFO

    # Full LLM request/response content (TRACE = on, WARN = off by default)
    io.jaiclaw.agent.LlmTraceLogger: WARN
```

With `AgentRuntime` at INFO, each LLM call logs:

```
INFO  AgentRuntime - LLM usage — request: 487 tokens, response: 583 tokens, total: 1,070 tokens
```

If you see unexpectedly high request token counts (e.g., 30,000+ for a simple query), check your `allow-bundled` configuration — all bundled skills are probably being loaded.

To debug what is being sent to the LLM, temporarily set `io.jaiclaw.agent.LlmTraceLogger: TRACE` to see the full system prompt and message history.

## Extending This Example

To implement a real travel data provider:

1. Create a class implementing `TravelDataProvider`
2. Register it as a `@Bean` in `TravelPlannerConfiguration` with a `@Profile` annotation
3. Use `ProxyAwareHttpClientFactory.createWithDefaults()` for HTTP calls (proxy-aware)
4. See `AmadeusApiTravelDataProvider` for the full pattern including OAuth2 token exchange
