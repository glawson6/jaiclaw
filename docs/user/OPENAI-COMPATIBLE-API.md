# OpenAI-Compatible API

Exposes a JaiClaw agent at `POST /v1/chat/completions`, so tools and SDKs that
speak the OpenAI wire format work against it unchanged.

Introduced in 1.2.0. **Off by default.**

## Enabling it

```yaml
jaiclaw:
  gateway:
    openai-api-enabled: true
    openai-api-session-strategy: per-request   # or by-user-header
```

> **This surface performs no authentication of its own.** It presents to a client
> as a model API, which is exactly what makes it useful and exactly why it is off
> by default. Front `/v1/**` with the same authentication as the rest of your
> admin surface before enabling it. When disabled it returns **404**, not 403 —
> a disabled surface should not advertise that it exists.

## Usage

```bash
curl http://localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"assistant","messages":[{"role":"user","content":"what is 2+2?"}]}'
```

```python
from openai import OpenAI
client = OpenAI(base_url="http://localhost:8080/v1", api_key="unused")

resp = client.chat.completions.create(
    model="assistant",                     # a JaiClaw agent id
    messages=[{"role": "user", "content": "what is 2+2?"}],
)
print(resp.choices[0].message.content)
```

Streaming works too (`stream=True`), delivered as `chat.completion.chunk` frames
terminated by `[DONE]`.

## What maps to what

| OpenAI field | JaiClaw meaning |
|---|---|
| `model` | **Agent id**, not a provider model name |
| `messages` | The last `user` message is what the agent answers |
| `stream` | SSE chunk framing |
| `temperature`, `top_p`, `max_tokens` | **Accepted and ignored** |

Sampling parameters are ignored deliberately: the deployment owns model
configuration, and honouring them would let any caller reconfigure the agent.
Accepting them silently keeps stock clients working.

## Session strategies

**`per-request`** (default) — each call is a fresh session. The client's `messages`
array is the entire history, which is what most OpenAI clients assume.

**`by-user-header`** — a durable session keyed on `X-JaiClaw-User`, so JaiClaw's
own session and memory features apply across calls:

```bash
curl ... -H 'X-JaiClaw-User: alice'
```

The header name is matched case-insensitively, per the HTTP spec.

## Streaming granularity

The runtime returns a complete message on this path, so a streamed response
arrives as one content frame between the role and stop frames. Clients see a
well-formed SSE stream — just not token-by-token. Token-level streaming is a
1.3.0 concern.

## See also

- [`WEBHOOK-CHANNEL.md`](./WEBHOOK-CHANNEL.md)
- [`EMERGENCY-STOP.md`](./EMERGENCY-STOP.md) — a paused gateway refuses here too
