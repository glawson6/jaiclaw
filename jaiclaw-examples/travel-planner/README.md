# Travel Planner Example

Multi-step trip planning — an Embabel GOAP agent researches flights and hotels, then assembles a complete itinerary.

## What This Demonstrates

- **Embabel GOAP** agent with parallel-capable actions (flights + hotels searched independently)
- **@Agent** with multiple **@Action** methods chained by type dependencies
- Domain records as blackboard state (TravelRequest → FlightOptions + HotelOptions → TripPlan)
- **Browser** module integration (Playwright-based web research, simulated in this example)
- **Voice** module for speech I/O (optional)
- LLM structured extraction with `createObject()`

## Architecture

Where this example fits in JaiClaw:

```
┌───────────────────────────────────────────────────────────┐
│                   TRAVEL PLANNER APP                       │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Voice (optional) │  [jaiclaw-voice] → speech I/O             │
├──────────────────┼────────────────────────────────────────┤
│ Orchestration    │  [Embabel GOAP] → parallel actions      │
│                  │  searchFlights + searchHotels → plan     │
├──────────────────┼────────────────────────────────────────┤
│ Browser          │  [jaiclaw-browser] → Playwright research  │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  User ──("plan a trip")──► Embabel AgentPlatform
                                  │
                       ┌──────────┴──────────┐
                       ▼                     ▼
                searchFlights          searchHotels
                  (Browser)              (Browser)
                       │                     │
                       ▼                     ▼
                 FlightOptions         HotelOptions
                       └──────────┬──────────┘
                                  ▼
                              TripPlan
                          (LLM assembles)
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI API key (Embabel works best with capable models)

## Build & Run

```bash
cd jaiclaw-examples/travel-planner
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Testing It

```bash
# Plan a trip
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Plan a 5-day trip to Tokyo for 2 people, budget $5000, departing April 15"}'

# Health check
curl http://localhost:8080/api/health
```
