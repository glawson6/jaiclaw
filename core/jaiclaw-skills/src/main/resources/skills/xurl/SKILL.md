---
name: xurl
description: "X (Twitter) API CLI for posting tweets, replying, searching, reading posts, managing followers, sending DMs, and uploading media. Use when asked to interact with X/Twitter. Requires xurl CLI with pre-configured auth."
alwaysInclude: false
requiredBins: [xurl]
platforms: [darwin, linux]
---

# X (Twitter) via xurl

CLI tool for the X API. All commands return JSON to stdout.

## Setup

Auth must be configured outside the agent session. Verify with:

```bash
xurl auth status
```

## Security Rules

- Never read, print, or reference `~/.xurl` (contains credentials)
- Never use `--verbose` / `-v` (can expose tokens)
- Never use credential flags inline (`--bearer-token`, `--consumer-key`, etc.)

## Quick Reference

| Action | Command |
|--------|---------|
| Post | `xurl post "Hello world!"` |
| Reply | `xurl reply POST_ID "Nice post!"` |
| Quote | `xurl quote POST_ID "My take"` |
| Delete | `xurl delete POST_ID` |
| Read | `xurl read POST_ID` |
| Search | `xurl search "QUERY" -n 10` |
| Who am I | `xurl whoami` |
| User lookup | `xurl user @handle` |
| Timeline | `xurl timeline -n 20` |
| Mentions | `xurl mentions -n 10` |
| Like/Unlike | `xurl like POST_ID` / `xurl unlike POST_ID` |
| Repost | `xurl repost POST_ID` / `xurl unrepost POST_ID` |
| Bookmark | `xurl bookmark POST_ID` / `xurl unbookmark POST_ID` |
| Follow | `xurl follow @handle` / `xurl unfollow @handle` |
| DM | `xurl dm @handle "message"` |
| List DMs | `xurl dms -n 10` |
| Upload media | `xurl media upload path/to/file.mp4` |
| Media status | `xurl media status MEDIA_ID` |

## Common Workflows

### Post with Image

```bash
xurl media upload photo.jpg       # note the media_id
xurl post "Check this out!" --media-id MEDIA_ID
```

### Search and Engage

```bash
xurl search "topic" -n 10
xurl like POST_ID
xurl reply POST_ID "Great point!"
```

## Raw API Access

```bash
xurl /2/users/me
xurl -X POST /2/tweets -d '{"text":"Hello"}'
xurl -X DELETE /2/tweets/1234567890
```

## Notes

- Post IDs and full URLs both work: `xurl read https://x.com/user/status/123`
- Leading `@` is optional for usernames
- Rate limits apply; 429 errors mean wait and retry
- Confirm with user before posting, replying, or sending DMs
