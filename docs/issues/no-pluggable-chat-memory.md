# Chat history doesn't survive pod restarts; `SessionManager` is not pluggable

**Module:** `jaiclaw-agent`
**Severity:** functional gap (memory loss on every redeploy) + extensibility gap (no SPI for durable backends)
**Affects:** any production deployment that wants chat-turn memory to survive pod restarts. Discovered in `jaiclaw-event-agent` where a public Telegram bot lost conversation context after every rollout — and we rollout a lot during iteration.

## Summary

`io.jaiclaw.agent.session.SessionManager` (`core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/session/SessionManager.java`) keeps every session and every turn in a process-local `ConcurrentHashMap<String, Session>`. **Within a pod lifetime, chat history works correctly** — the manager appends user/assistant messages on each turn, and `AgentRuntime.executeSync()` reads them back when building the prompt. Token counts and the framework's own prompt-cache reads confirm this: the cumulative cached tokens grow turn-over-turn even when the bare request-tokens delta stays small.

What does *not* work:

1. **Restart loses everything.** Every `kubectl rollout restart` (or pod evict, or HPA scale-down) wipes the map. Telegram users see the bot suddenly forget everything that happened before the last deploy.
2. **`SessionManager` is a concrete class, not an SPI.** There's no `@ConditionalOnMissingBean` hook to slot in a Redis-, Postgres-, or in-cluster-cache-backed alternative. Subclassing works in theory but is brittle (the class is not designed for it: most methods are non-final, but the internals couple tightly to the private `sessions` map).
3. **No SPI means no easy way to test history-related logic with a fake.** A Spock spec that wants to assert "session X has N messages after this flow" has to construct the real `SessionManager`, hit it with real `getOrCreate`/`appendMessage` calls, and read the real map.

## Reproduction

Concrete signal from `jaiclaw-event-agent` running at `events.taptech.net` after a series of Telegram turns to the same chat (sessionKey constant across turns — confirmed in the gateway log):

```
17:09  LLM usage — request: 3026 tokens   ← first turn (system prompt + tools)
17:10  LLM usage — request: 1613 tokens   ← second turn, cached
17:14  LLM usage — request: 7707 tokens   ← uploaded a flyer (image bytes)
17:15  LLM usage — request:  874 tokens   ← LLM cache — read: 3579 tokens
17:16  LLM usage — request:  653 tokens   ← LLM cache — read: 4027 tokens
17:17  LLM usage — request:  240 tokens   ← LLM cache — read: 4475 tokens
17:18  LLM usage — request:  325 tokens   ← LLM cache — read: 4475 tokens
                                            ← rollout restart here
17:28  LLM usage — request: 4843 tokens   ← cold start: system prompt + tools again, no prior turns
17:29  LLM usage — request:  882 tokens   ← LLM cache — read: 4475 tokens (rebuilding)
```

The `LLM cache — read` line growing from 3579 → 4027 → 4475 tells you the prompt-cached content (system prompt + tool defs + history) is growing. So **history IS being sent**. But after the 17:18 / 17:28 boundary the request-tokens jump back to 4843 because the pod restarted and the in-memory session map was wiped — the bot is talking to a stranger again.

User-visible symptom: bot replies "I don't have context from a previous conversation" or "I'm not sure what table you're referring to" when asked about something it discussed minutes earlier (across a rollout).

## Possible fixes

### (a) Extract `SessionManager` to an SPI, ship an `InMemorySessionManager` default (recommended)

Convert the current class to an interface; rename the existing class to `InMemorySessionManager` and have it implement that interface. All current public methods stay on the interface. Auto-config registers `InMemorySessionManager` as `@ConditionalOnMissingBean SessionManager`.

```java
public interface SessionManager {
    Session getOrCreate(String sessionKey, String agentId);
    void appendMessage(String sessionKey, Message message);
    Optional<Session> get(String sessionKey);
    void replaceMessages(String sessionKey, List<Message> newMessages);
    Session transitionState(String sessionKey, SessionState newState);
    Session close(String sessionKey);
    void reset(String sessionKey);
    List<Session> listSessions();
    List<Session> listActiveSessions();
    int messageCount(String sessionKey);
    boolean exists(String sessionKey);
    int sessionCount();
    void setTenantGuard(TenantGuard tenantGuard);
    void setHookDispatcher(AgentHookDispatcher hooks);
}
```

Pros: smallest possible change. Backward-compatible — every existing caller already programs to the public surface. Lets downstream apps drop in their own bean without subclassing tricks.
Cons: doesn't ship a durable backend by itself. Apps still have to bring their own.

### (b) Ship a `RedisSessionManager` alongside the SPI

Following the same pattern as `event-agent-store-redis` in `jaiclaw-event-agent`, ship a `jaiclaw-session-redis` extension module that implements `SessionManager` on top of `StringRedisTemplate`. Two key shapes per session:

```
session:{tenantPrefix}:{sessionKey}       — JSON-serialised Session record
session:{tenantPrefix}:{sessionKey}:msgs  — LIST of JSON Message records
```

`appendMessage` does an `RPUSH` to the list; `get`/`getOrCreate` reads the value + the list. `EXPIRE` on both keys with a configurable TTL (e.g. `jaiclaw.agent.session.ttl: P30D`).

Pros: gives a working durable backend the way `jaiclaw-event-agent` already uses Redis for its event store. Restarts don't lose memory. Works across replicas if you ever HA-scale the agent.
Cons: introduces a new optional dep on Spring Data Redis. Worth gating behind `@ConditionalOnClass(StringRedisTemplate.class)` + `@ConditionalOnProperty(jaiclaw.agent.session.backend=redis)`.

### (c) Add per-turn observability log lines

Independent of (a)/(b): drop one INFO log per agent turn that names the history size, e.g.:

```
INFO AgentRuntime - session=chat:telegram:... — history=12 msgs, prompt-tokens=240 (cached=4475), response-tokens=500
```

Today the operator has to triangulate from token counts + cache reads to infer history size. Useful for diagnostic work like the one that produced this issue doc.

Pros: tiny change, helps every operator.
Cons: trivially small; bundle with (a).

## Recommended fix

(a) immediately — it's the contract change that unblocks everything else. Land (c) in the same PR for free observability. Then ship (b) as a follow-up extension module when there's appetite (it's not in the critical path of the SPI work).

## Workarounds available today

None that are clean. We considered:

1. **Subclass `SessionManager`** and override `getOrCreate`/`appendMessage`/`get` to hit Redis instead of the local map. Brittle — `appendMessage` uses `sessions.computeIfPresent(key, ...)` and `get` enforces tenant isolation via a non-overridable code path that reads `sessions` directly. Subclasses can't substitute the backing store without re-implementing the bits we shouldn't have to re-implement.
2. **Stickiness via affinity** so a given Telegram chat always lands on the same pod. Helps within a deploy lifetime but doesn't fix the rollout case at all, and doesn't survive HPA.
3. **Custom `AgentLoopDelegate`** that ignores the framework's session manager and uses its own Redis-backed history. Pulls the loop logic out of the framework into the app — large surface area, high re-implementation cost.

The least-bad option for `jaiclaw-event-agent` is to **accept memory loss on every redeploy** until (a) ships, and minimise the impact by keeping deploys infrequent.

## Related code

- `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/session/SessionManager.java` — the class that becomes an interface in (a).
- `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntime.java` — `executeSync()` calls `sessionManager.appendMessage(...)` at lines 311, 396, 442, 594. None of these need to change under (a); they all program to the public surface.
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayService.java` — line 237 calls `sessionManager.getOrCreate(sessionKey, defaultAgentId)`. Unchanged under (a).
- `jaiclaw-spring-boot-starter/src/main/java/io/jaiclaw/autoconfigure/JaiClawAgentAutoConfiguration.java` — the `sessionManager` bean factory becomes `@ConditionalOnMissingBean`.

## Reproduction smoke test once fixed

After (a) ships:

```java
@Configuration
public class MyAppConfig {
    @Bean
    @Primary
    public SessionManager sessionManager() {
        return new MyRedisSessionManager(redisTemplate, ...);
    }
}
```

…replaces the in-memory default, and three Telegram turns *across a pod restart* should produce monotonically growing prompt-cache reads on the second turn after restart — no "I don't have context" replies.

---

## Resolution

**Shipped in 0.9.1.** Options (a) and (c) landed together:

- (a) `SessionManager` is now an interface in `io.jaiclaw.agent.session`; the
  previous concrete class moved to `InMemorySessionManager` (same body,
  same three constructors, same `ConcurrentHashMap` store). The
  framework bean factory registers the default via
  `@ConditionalOnMissingBean(SessionManager.class)` in
  `JaiClawAgentAutoConfiguration` — downstream apps replace it by
  declaring their own `@Bean SessionManager`.
- (c) `AgentRuntime` now emits a per-turn INFO line
  `Session turn — session={} history={} msgs, prompt={} tokens (cached={}),
  response={} tokens` immediately after the existing `LLM usage` / `LLM
  cache` block, giving operators a single grep-able diagnostic tying
  conversation depth to token usage.

A new regression spec — `SessionManagerSpiSpec` in
`jaiclaw-spring-boot-starter` — locks both the interface contract and
the `@ConditionalOnMissingBean` annotation so a future refactor can't
silently re-introduce a concrete class or drop the conditional.

Option (b), the `jaiclaw-session-redis` extension module, is tracked as
a separate follow-up. The SPI is the gating dependency; once 0.9.1
ships, a Redis backend is a self-contained add-on.
