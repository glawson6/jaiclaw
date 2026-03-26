---
name: home-assistant
description: "Home Assistant REST API for controlling smart home devices, automations, and scenes. Use when asked to control lights, thermostats, switches, locks, sensors, or any smart home device. Also for checking device status, triggering automations, or managing scenes. Requires HOME_ASSISTANT_URL and HOME_ASSISTANT_TOKEN."
alwaysInclude: false
requiredBins: [curl]
platforms: [darwin, linux]
---

# Home Assistant

Control smart home devices via the Home Assistant REST API.

## Setup

Set environment variables:

```bash
export HOME_ASSISTANT_URL="http://homeassistant.local:8123"
export HOME_ASSISTANT_TOKEN="your-long-lived-access-token"
```

To create a token: Home Assistant > Profile > Long-Lived Access Tokens > Create Token.

## API Basics

All requests use:

```bash
curl -s "$HOME_ASSISTANT_URL/api/..." \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json"
```

## Device Control

### Lights

```bash
# Turn on
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/light/turn_on" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "light.living_room"}'

# Turn on with brightness and color
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/light/turn_on" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "light.living_room", "brightness": 200, "color_name": "blue"}'

# Turn off
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/light/turn_off" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "light.living_room"}'

# Toggle
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/light/toggle" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "light.living_room"}'
```

### Switches

```bash
# Turn on/off
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/switch/turn_on" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "switch.coffee_maker"}'

curl -s -X POST "$HOME_ASSISTANT_URL/api/services/switch/turn_off" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "switch.coffee_maker"}'
```

### Climate / Thermostat

```bash
# Set temperature
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/climate/set_temperature" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "climate.thermostat", "temperature": 72}'

# Set HVAC mode (heat, cool, auto, off)
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/climate/set_hvac_mode" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "climate.thermostat", "hvac_mode": "heat"}'
```

### Locks

```bash
# Lock
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/lock/lock" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "lock.front_door"}'

# Unlock (confirm with user first!)
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/lock/unlock" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "lock.front_door"}'
```

### Covers (Blinds, Garage Doors)

```bash
# Open/close
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/cover/open_cover" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "cover.garage_door"}'

# Set position (0=closed, 100=open)
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/cover/set_cover_position" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "cover.blinds", "position": 50}'
```

### Media Players

```bash
# Play/pause
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/media_player/media_play_pause" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "media_player.living_room"}'

# Set volume (0.0 - 1.0)
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/media_player/volume_set" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "media_player.living_room", "volume_level": 0.5}'
```

## Query State

### Get All States

```bash
curl -s "$HOME_ASSISTANT_URL/api/states" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '.[].entity_id'
```

### Get Specific Entity State

```bash
curl -s "$HOME_ASSISTANT_URL/api/states/light.living_room" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '{state: .state, brightness: .attributes.brightness}'
```

### Get States by Domain

```bash
# All lights
curl -s "$HOME_ASSISTANT_URL/api/states" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '[.[] | select(.entity_id | startswith("light."))] | .[] | {entity_id, state}'

# All sensors
curl -s "$HOME_ASSISTANT_URL/api/states" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '[.[] | select(.entity_id | startswith("sensor."))] | .[] | {entity_id, state, unit: .attributes.unit_of_measurement}'

# All climate devices
curl -s "$HOME_ASSISTANT_URL/api/states" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '[.[] | select(.entity_id | startswith("climate."))] | .[] | {entity_id, state, temperature: .attributes.current_temperature}'
```

## Automations and Scenes

### Trigger Automation

```bash
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/automation/trigger" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "automation.morning_routine"}'
```

### Activate Scene

```bash
curl -s -X POST "$HOME_ASSISTANT_URL/api/services/scene/turn_on" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"entity_id": "scene.movie_night"}'
```

### List Automations and Scenes

```bash
# List automations
curl -s "$HOME_ASSISTANT_URL/api/states" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '[.[] | select(.entity_id | startswith("automation."))] | .[] | {entity_id, state, name: .attributes.friendly_name}'

# List scenes
curl -s "$HOME_ASSISTANT_URL/api/states" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '[.[] | select(.entity_id | startswith("scene."))] | .[] | {entity_id, name: .attributes.friendly_name}'
```

## History and Logs

```bash
# Get history for an entity (last 24h)
curl -s "$HOME_ASSISTANT_URL/api/history/period?filter_entity_id=sensor.temperature" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '.[0] | .[-5:]'

# Error log
curl -s "$HOME_ASSISTANT_URL/api/error_log" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN"
```

## Service Discovery

```bash
# List all available services
curl -s "$HOME_ASSISTANT_URL/api/services" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '.[].domain'

# List services for a domain
curl -s "$HOME_ASSISTANT_URL/api/services" \
  -H "Authorization: Bearer $HOME_ASSISTANT_TOKEN" | jq '.[] | select(.domain == "light") | .services | keys'
```

## Safety Rules

- Always confirm with user before unlocking locks or opening garage doors
- Confirm before changing thermostat settings significantly
- Confirm before triggering automations that control multiple devices
- When in doubt about entity_id, list entities first and confirm with user
- Never expose the HOME_ASSISTANT_TOKEN in logs or chat

## Common Entity ID Patterns

| Domain | Pattern | Example |
|--------|---------|---------|
| Lights | `light.*` | `light.living_room` |
| Switches | `switch.*` | `switch.coffee_maker` |
| Sensors | `sensor.*` | `sensor.temperature` |
| Climate | `climate.*` | `climate.thermostat` |
| Locks | `lock.*` | `lock.front_door` |
| Covers | `cover.*` | `cover.garage_door` |
| Media | `media_player.*` | `media_player.tv` |
| Cameras | `camera.*` | `camera.front_porch` |
| Binary sensors | `binary_sensor.*` | `binary_sensor.motion` |
| Automations | `automation.*` | `automation.morning` |
| Scenes | `scene.*` | `scene.movie_night` |
| Scripts | `script.*` | `script.goodnight` |

## Notes

- Entity IDs use lowercase with underscores
- Home Assistant typically runs on port 8123
- The API follows REST conventions with JSON payloads
- Use `jq` to filter and format JSON responses
- Check `/api/config` for system info and version
