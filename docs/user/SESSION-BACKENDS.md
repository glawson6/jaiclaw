# Session Backends

`SessionManager` is JaiClaw's SPI for chat-history storage. The
framework ships one default and one optional durable backend:

| Backend | Module | When to use | Survives pod restart? |
|---|---|---|---|
| **In-memory** (default) | `jaiclaw-agent` (`InMemorySessionManager`) | Local development, single-pod deployments, sessions that are cheap to lose | No |
| **Redis** | `jaiclaw-session-redis` | Any production deployment; multi-replica agents; anywhere users notice "the bot forgot our conversation" after a redeploy | Yes |

Adopters can also plug in their own `SessionManager` bean (Postgres,
JCache, encrypted-at-rest, etc.) — the framework respects
`@ConditionalOnMissingBean(SessionManager.class)` and steps aside.

---

## In-memory (default)

Registered automatically by `JaiClawAgentAutoConfiguration` as the
`@ConditionalOnMissingBean SessionManager`. State lives in a
`ConcurrentHashMap<String, Session>` inside the JVM and is lost on
every process exit — pod restart, HPA scale-down, or `kubectl rollout
restart`.

**Symptom** in production without a durable backend: after a
rollout, users report "I don't have context from a previous
conversation." Framework token counts confirm it — the second turn
after restart has request-tokens back at cold-start levels because
the in-memory session map was wiped.

For any deployment that redeploys more than once per session's
useful lifetime, switch to Redis.

---

## Redis (opt-in)

### Dependency

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-session-redis</artifactId>
</dependency>
```

`spring-boot-starter-data-redis` comes as an optional transitive; the
consumer app must also configure a Redis connection in the usual
Spring way — e.g. `spring.data.redis.host=localhost` — so that
`StringRedisTemplate` gets autowired.

### Enable

```yaml
jaiclaw:
  agent:
    session:
      backend: redis                   # default is unset → in-memory
      redis:
        prefix: "jaiclaw:sessions"     # optional; default shown
        ttl: "P30D"                    # optional; ISO-8601 Duration; default 30 days

spring:
  data:
    redis:
      host: redis.internal
      port: 6379
      # ... auth / cluster / sentinel config as usual
```

Set `jaiclaw.agent.session.backend=redis` and the autoconfig activates.
Leave it unset (or set to anything else) and the in-memory default
wins — same app, same jar.

### Key layout

Every session gets two keys plus one shared per-tenant index:

```
{prefix}:{tenantId}:{sessionKey}          — JSON metadata blob
{prefix}:{tenantId}:{sessionKey}:msgs     — Redis LIST of JSON message envelopes
{prefix}:{tenantId}:idx:sessions          — SET of sessionKeys owned by the tenant
```

In SINGLE-tenant mode `{tenantId}` is empty (the physical key becomes
`{prefix}::{sessionKey}` etc.) so the key shape is stable across a
SINGLE → MULTI rollout.

### Semantics

- `appendMessage` — `RPUSH` on the messages list + `EXPIRE`. Refreshes
  the metadata blob's `lastActiveAt` on the same call.
- `get` — reads metadata + `LRANGE` messages, validates tenant against
  the decoded `Session.tenantId`.
- `transitionState` — `WATCH/MULTI/EXEC` on the metadata key. Retries
  up to 3 times if another writer beat it; returns `null` if the
  session was reset between reads.
- `close` — `transitionState(CLOSED)` + fires `SessionEndedEvent`. Does
  not delete the keys; they expire via TTL. Matches
  `InMemorySessionManager`.
- `reset` — deletes both per-session keys + SREM from the index.
- `listSessions` — SMEMBERS the index, pipelines GET each metadata
  blob, prunes stale members via SREM. Skips loading each session's
  messages.
- `messageCount` — `LLEN`, no deserialization.

**Consistency note.** `sessionCount` returns `SCARD` on the tenant
index. It may temporarily over-count sessions whose metadata has
expired but haven't been read yet (and thus pruned lazily). This is
eventually consistent; use `listSessions().size()` if you need a
freshly-verified count.

### TTL

Every write path refreshes TTL on both per-session keys. Default is
30 days. Reduce for high-traffic bots that produce many short-lived
sessions:

```yaml
jaiclaw:
  agent:
    session:
      redis:
        ttl: "P7D"       # 7 days
```

The tenant index set has no TTL — it's small and pruned lazily on
`listSessions()`.

### Serialization

Messages are stored as one JSON envelope per LIST element:

```json
{ "type": "user", "id": "u1", "timestamp": "2026-07-20T12:00:00Z",
  "content": "hello",
  "senderId": "alice",
  "metadata": { "locale": "en" },
  "media": [ { "mimeType": "image/png",
               "bytes": "iVBORw0KGgo...",
               "filename": "flyer.png" } ] }
```

`bytes` is base64-encoded (Jackson default for `byte[]`). Large
attachments inline into the message payload; if your bot handles
big uploads regularly, consider externalising blob storage in a
follow-up (deferred).

The codec tolerates unknown envelope fields on decode and missing
optional fields default sensibly — a future non-breaking
Message-subtype field can roll out without breaking older Redis
payloads.

---

## Writing a custom `SessionManager`

Any `@Bean SessionManager` in your application context wins over
both the InMemory default and the Redis backend (they're both
gated on `@ConditionalOnMissingBean`).

```java
@Configuration
public class MyAppConfig {
    @Bean
    public SessionManager sessionManager(MyStore store, TenantGuard guard) {
        MyDurableSessionManager m = new MyDurableSessionManager(store);
        m.setTenantGuard(guard);
        return m;
    }
}
```

The SPI (12 methods + 2 setter injections) is documented on the
interface in
`core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/session/SessionManager.java`.
The reference implementation in
`core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/session/InMemorySessionManager.java`
is a good starting template — every method is short, and the tenant
+ hook wiring is handled with a `TenantGuard` field and a
`fireVoid(HookEvent)` helper.

**Hook semantics.** Fire `SessionStartedEvent` on first
`getOrCreate` creation, `SessionEndedEvent(reason="closed")` on
`close`, `SessionEndedEvent(reason="reset")` on `reset`. Hook
failures must fail-safe — catch and log, don't propagate.

---

## Verification

Reproduce the issue's symptom with `jaiclaw-shell` (or any agent
app) using the Redis backend:

1. Deploy with `jaiclaw.agent.session.backend=redis` +
   `spring.data.redis.host=<redis>` + `jaiclaw-session-redis` on
   the classpath.
2. Hold three turns with the agent over the same `sessionKey`.
3. `kubectl rollout restart deployment/<my-agent>`.
4. Turn 4 — cumulative `LLM cache read` tokens should include
   turns 1-3, not restart at zero.
5. Redis-CLI check: `KEYS "jaiclaw:sessions:*"` — you should see
   the two per-session keys + the index.

---

## Deferred / future work

- **Testcontainers integration spec** — a spec that runs
  `RedisSessionManager` against a real Redis container. Deferred
  because CI doesn't universally have Docker.
- **Reactive variant** — `SessionManager` is synchronous;
  a `ReactiveSessionManager` SPI is a larger design.
- **Blob externalisation** for large `MediaAttachment` payloads —
  today they inline into the message JSON.
- **`SessionEvictionPolicy`** — Redis TTL handles time-based
  eviction only; LRU / count-cap is a future add.
