# Webhook Channel

Accepts authenticated inbound HTTP payloads from any external system and hands
them to an agent session running under a restricted tool profile.

Introduced in 1.2.0 as `jaiclaw-channel-webhook`. **Off by default.**

## Configuration

```yaml
jaiclaw:
  channels:
    webhook:
      enabled: true
      routes:
        - route-id: github-pr
          secret: ${GITHUB_WEBHOOK_SECRET}   # required — no secret, no route
          tenant-id: acme
          agent-id: reviewer
          session-mode: ISOLATED             # or PER_ROUTE
```

## The threat model

**Anyone on the network can POST here, and the body reaches a language model with
tools attached.** Every design choice below follows from that.

| Situation | Behaviour |
|---|---|
| No `secret` configured | Route is **disabled**, not left open |
| Missing signature header | `401` |
| Bad signature | `401` |
| Tampered body | `401` |
| Unknown route | `401` — *identical* to a bad signature |

Unknown routes return the same response as bad signatures deliberately, so a
prober cannot enumerate which routes exist.

## Signing

HMAC-SHA256 over the **raw body bytes**, hex-encoded, in `X-JaiClaw-Signature`:

```bash
BODY='{"event":"pull_request","number":42}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $2}')

curl -X POST http://localhost:8080/webhooks/github-pr \
  -H "X-JaiClaw-Signature: $SIG" \
  -H 'Content-Type: application/json' \
  -d "$BODY"
```

The signature is over the bytes as received — a proxy that re-serialises JSON
will break verification.

## Tool profile

Sessions created from a webhook run under **`WEBHOOK_SAFE`**: read-only research
(web search, web fetch, media analysis) and nothing that writes. No shell, no
file writes, no delegation.

**A route cannot be configured wider than `WEBHOOK_SAFE`.** Asking for `FULL` in
YAML yields `WEBHOOK_SAFE`; asking for something narrower (`MINIMAL`, `NONE`) is
honoured. The clamp is in `WebhookRoute`'s compact constructor, so it cannot be
bypassed by any construction path.

## Session modes

**`ISOLATED`** (default) — a fresh session per delivery. No state carries between
unrelated payloads, which is almost always what you want for webhook traffic.

**`PER_ROUTE`** — one long-lived session for the route, so context accumulates.
Use only when deliveries are genuinely a continuing conversation.

## Replies

The channel is **inbound only**. `sendMessage` returns a `Failure` with
`errorCode=webhook_inbound_only` rather than silently dropping the reply, so a
misrouted response is visible instead of appearing to work.

A route may carry a `callbackUrl` for a consumer to POST replies back; this
adapter does not itself make outbound HTTP calls.

## See also

- [`OPENAI-COMPATIBLE-API.md`](./OPENAI-COMPATIBLE-API.md)
- [`BUDGETS-AND-GUARDS.md`](./BUDGETS-AND-GUARDS.md) — approval floors apply here too
