---
name: gog
description: "Google Workspace CLI for Gmail, Calendar, Drive, Contacts, Sheets, and Docs via the gog CLI. Use when asked to send emails, check calendar, search Drive, read/update spreadsheets, or interact with Google Workspace."
alwaysInclude: false
requiredBins: [gog]
platforms: [darwin, linux]
---

# Google Workspace (gog)

Use `gog` for Gmail, Calendar, Drive, Contacts, Sheets, and Docs. Requires OAuth setup.

## Install

```bash
brew install steipete/tap/gogcli
```

## Setup (Once)

```bash
gog auth credentials /path/to/client_secret.json
gog auth add you@gmail.com --services gmail,calendar,drive,contacts,docs,sheets
gog auth list
```

## Gmail

```bash
# Search threads
gog gmail search 'newer_than:7d' --max 10

# Search individual messages
gog gmail messages search "in:inbox from:sender@example.com" --max 20

# Send plain text
gog gmail send --to a@b.com --subject "Hi" --body "Hello"

# Send multi-line (via file or stdin)
gog gmail send --to a@b.com --subject "Hi" --body-file ./message.txt
gog gmail send --to a@b.com --subject "Hi" --body-file - <<'EOF'
Hello,

Paragraph here.

Best regards
EOF

# Send HTML
gog gmail send --to a@b.com --subject "Hi" --body-html "<p>Hello</p>"

# Create draft
gog gmail drafts create --to a@b.com --subject "Hi" --body-file ./message.txt

# Reply
gog gmail send --to a@b.com --subject "Re: Hi" --body "Reply" --reply-to-message-id <msgId>
```

## Calendar

```bash
# List events
gog calendar events <calendarId> --from <iso> --to <iso>

# Create event
gog calendar create <calendarId> --summary "Title" --from <iso> --to <iso>

# Create with color
gog calendar create <calendarId> --summary "Title" --from <iso> --to <iso> --event-color 7

# Update event
gog calendar update <calendarId> <eventId> --summary "New Title"

# Show available colors (IDs 1-11)
gog calendar colors
```

## Drive

```bash
gog drive search "query" --max 10
```

## Contacts

```bash
gog contacts list --max 20
```

## Sheets

```bash
# Read cells
gog sheets get <sheetId> "Tab!A1:D10" --json

# Update cells
gog sheets update <sheetId> "Tab!A1:B2" --values-json '[["A","B"],["1","2"]]' --input USER_ENTERED

# Append rows
gog sheets append <sheetId> "Tab!A:C" --values-json '[["x","y","z"]]' --insert INSERT_ROWS

# Clear range
gog sheets clear <sheetId> "Tab!A2:Z"

# Get sheet metadata
gog sheets metadata <sheetId> --json
```

## Docs

```bash
# Export to text
gog docs export <docId> --format txt --out /tmp/doc.txt

# Print content
gog docs cat <docId>
```

## Notes

- Set `GOG_ACCOUNT=you@gmail.com` to avoid repeating `--account`
- Use `--json` plus `--no-input` for scripting
- `--body` does not unescape `\n`; use `--body-file` or heredoc for multi-line
- Confirm before sending mail or creating events
- `gog gmail search` returns threads; `gog gmail messages search` returns individual messages
