---
name: weather
description: "Get current weather and forecasts via wttr.in or Open-Meteo. Use when user asks about weather, temperature, or forecasts for any location. No API key needed."
alwaysInclude: false
requiredBins: [curl]
platforms: [darwin, linux]
---

# Weather Skill

Get current weather conditions and forecasts using wttr.in (no API key required).

## When to Use

- "What's the weather?"
- "Will it rain today/tomorrow?"
- "Temperature in [city]"
- "Weather forecast for the week"
- Travel planning weather checks

## When NOT to Use

- Historical weather data — use weather archives
- Severe weather alerts — check official NWS sources
- Aviation/marine weather — use specialized services

## Commands

### Current Weather

```bash
# One-line summary
curl -s "wttr.in/London?format=3"

# Detailed current conditions
curl -s "wttr.in/London?0"

# Specific format
curl -s "wttr.in/New+York?format=%l:+%c+%t+(feels+like+%f),+%w+wind,+%h+humidity"
```

### Forecasts

```bash
# 3-day forecast
curl -s "wttr.in/London"

# Week forecast
curl -s "wttr.in/London?format=v2"

# Tomorrow only
curl -s "wttr.in/London?1"
```

### JSON Output

```bash
curl -s "wttr.in/London?format=j1"
```

### Format Codes

- `%c` — Weather condition emoji
- `%t` — Temperature
- `%f` — "Feels like"
- `%w` — Wind
- `%h` — Humidity
- `%p` — Precipitation
- `%l` — Location

## Quick Responses

**"What's the weather?"**
```bash
curl -s "wttr.in/{location}?format=%l:+%c+%t+(feels+like+%f),+%w+wind,+%h+humidity"
```

**"Will it rain?"**
```bash
curl -s "wttr.in/{location}?format=%l:+%c+%p"
```

## Notes

- No API key needed (uses wttr.in)
- Rate limited; don't spam requests
- Works for most global cities
- Supports airport codes: `curl wttr.in/ORD`
- Always include a city or location in the query
