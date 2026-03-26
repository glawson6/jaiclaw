---
name: goplaces
description: "Query Google Places API for text search, place details, resolve, and reviews via the goplaces CLI. Use when asked about nearby places, restaurants, businesses, or location lookups. Requires GOOGLE_PLACES_API_KEY."
alwaysInclude: false
requiredBins: [goplaces]
platforms: [darwin, linux]
---

# Google Places (goplaces)

Modern Google Places API CLI. Human-readable output by default, `--json` for scripts.

## Install

```bash
brew install steipete/tap/goplaces
```

## Setup

Set `GOOGLE_PLACES_API_KEY` environment variable.

## Common Commands

```bash
# Search for places
goplaces search "coffee" --open-now --min-rating 4 --limit 5

# Search near a location
goplaces search "pizza" --lat 40.8 --lng -73.9 --radius-m 3000

# Resolve an address/place name
goplaces resolve "Soho, London" --limit 5

# Get place details with reviews
goplaces details <place_id> --reviews

# JSON output
goplaces search "sushi" --json

# Paginate results
goplaces search "pizza" --page-token "NEXT_PAGE_TOKEN"
```

## Notes

- `--no-color` or `NO_COLOR` env var disables ANSI color
- Price levels: 0 (free) to 4 (very expensive)
- Type filter accepts one `--type` value per query
