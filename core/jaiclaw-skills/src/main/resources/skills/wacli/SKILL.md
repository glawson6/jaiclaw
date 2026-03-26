---
name: wacli
description: "Send WhatsApp messages and search/sync WhatsApp history via the wacli CLI. Use when asked to message someone on WhatsApp or search WhatsApp conversation history. NOT for normal user chats."
alwaysInclude: false
requiredBins: [wacli]
platforms: [darwin, linux]
---

# WhatsApp CLI (wacli)

Use `wacli` to send WhatsApp messages to third parties or search WhatsApp history.

Do NOT use for normal user chats — JaiClaw routes those automatically via the WhatsApp channel adapter.

## Install

```bash
brew install steipete/tap/wacli
# or
go install github.com/steipete/wacli/cmd/wacli@latest
```

## Safety

- Require explicit recipient + message text
- Confirm recipient and message before sending
- If anything is ambiguous, ask a clarifying question

## Setup

```bash
wacli auth          # QR login + initial sync
wacli doctor        # Check setup
wacli sync --follow # Continuous sync
```

## Find Chats and Messages

```bash
# List recent chats
wacli chats list --limit 20

# Search for a contact
wacli chats list --query "name or number"

# Search messages
wacli messages search "query" --limit 20

# Search in specific chat
wacli messages search "invoice" --chat <jid>

# Search with date range
wacli messages search "invoice" --after 2025-01-01 --before 2025-12-31
```

## Send Messages

```bash
# Text message
wacli send text --to "+14155551212" --message "Hello!"

# Group message
wacli send text --to "1234567890-123456789@g.us" --message "Running 5 min late."

# File with caption
wacli send file --to "+14155551212" --file /path/to/doc.pdf --caption "Agenda"
```

## History Backfill

```bash
wacli history backfill --chat <jid> --requests 2 --count 50
```

## Notes

- Store dir: `~/.wacli` (override with `--store`)
- Use `--json` for machine-readable output
- JIDs: direct = `<number>@s.whatsapp.net`, groups = `<id>@g.us`
- Backfill requires phone online; results are best-effort
