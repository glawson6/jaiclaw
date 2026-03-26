---
name: gifgrep
description: "Search GIF providers (Tenor/Giphy), download results, and extract stills/sheets. Use when asked to find, search for, or share GIFs."
alwaysInclude: false
requiredBins: [gifgrep]
platforms: [darwin, linux]
---

# GIF Search

Use `gifgrep` to search GIF providers, download results, and extract stills or contact sheets.

## Install

```bash
brew install steipete/tap/gifgrep
# or
go install github.com/steipete/gifgrep/cmd/gifgrep@latest
```

## Quick Start

```bash
# Search for GIFs
gifgrep cats --max 5

# Get URLs only
gifgrep cats --format url | head -n 5

# JSON output
gifgrep search --json cats | jq '.[0].url'

# Download a GIF
gifgrep cats --download --max 1
```

## Stills and Sheets

```bash
# Extract a single frame
gifgrep still ./clip.gif --at 1.5s -o still.png

# Create a contact sheet (grid of frames)
gifgrep sheet ./clip.gif --frames 9 --cols 3 -o sheet.png
```

## Providers

- Default: auto-selects Tenor
- `--source tenor` or `--source giphy`
- `GIPHY_API_KEY` required for `--source giphy`

## Notes

- `--json` for machine-readable results with `id`, `title`, `url`, `preview_url`, `tags`, `width`, `height`
- `--download` saves to `~/Downloads`
- `--reveal` opens the download in Finder
