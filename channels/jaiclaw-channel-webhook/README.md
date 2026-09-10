# jaiclaw-channel-webhook

Accepts authenticated inbound HTTP payloads from any external system and hands
them to an agent session under a restricted tool profile.

**Opt-in**, inbound only.

## Quick start

```yaml
jaiclaw:
  channels:
    webhook:
      enabled: true
      routes:
        - route-id: github-pr
          secret: ${GITHUB_WEBHOOK_SECRET}
          tenant-id: acme
          agent-id: reviewer
          session-mode: ISOLATED
```

```bash
BODY='{"event":"pull_request","number":42}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $2}')
curl -X POST localhost:8080/webhooks/github-pr \
  -H "X-JaiClaw-Signature: $SIG" -d "$BODY"
```

## Threat model

Anyone on the network can POST here, and the body reaches a language model with
tools attached. Everything therefore fails closed:

| Situation | Result |
|---|---|
| No `secret` configured | Route **disabled**, logged as an error |
| Missing / bad signature | `401` |
| Tampered body | `401` |
| Unknown route | `401` — *identical* to a bad signature, so probers cannot enumerate routes |

Sessions are clamped to `ToolProfile.WEBHOOK_SAFE` in `WebhookRoute`'s compact
constructor, so a route asking for `FULL` gets `WEBHOOK_SAFE` regardless of how
it was constructed. Narrower requests are honoured.

The signature is over the **raw body bytes** — a proxy that re-serialises JSON
will break verification.

## Docs

[`docs/user/WEBHOOK-CHANNEL.md`](../../docs/user/WEBHOOK-CHANNEL.md)
