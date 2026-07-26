# Topics / Pub-Sub Layer — Implementation Plan (v2)

> **Status:** BACKLOG. Revised design, not yet scheduled. v2 filed
> 2026-07-16, superseding v1 (same file name, `-V2` suffix) after the
> architecture review in `TOPICS-PUB-SUB-PLAN-REVIEW.md`. Estimated
> effort: **5–7 days of focused work** (base module + filter-chain core
> change ~3–4 days, hardening ~1–2 days, shell/MCP surfaces + docs ~1 day).
>
> **Feature summary:** first-class Topic + Follow layer letting
> arbitrary code fan a single message out to many
> `(channelId, accountId, peerId)` followers across Telegram, Slack,
> Discord, etc. Two-way: users can `/follow <topic>` from a chat
> (Telegram in v1), and any publisher can push to that topic.
>
> **Sibling plans:** matches the shape of
> [`KANBAN-IMPLEMENTATION-PLAN.md`](KANBAN-IMPLEMENTATION-PLAN.md),
> [`OAUTH-IMPLEMENTATION-PLAN.md`](OAUTH-IMPLEMENTATION-PLAN.md),
> [`PIPELINE-STRATEGY.md`](pipeline/PIPELINE-STRATEGY.md), and
> [`COMPLIANCE-IMPLEMENTATION-PLAN.md`](COMPLIANCE-IMPLEMENTATION-PLAN.md).

---

## Changes from v1 (review findings addressed)

| Review finding | v2 resolution |
|---|---|
| B1 — single `GatewayMessageFilter` slot; 2nd bean breaks boot | New **Work item 0**: composite ordered filter chain in gateway + starter |
| B2 — `/subscribe` collides with paid-subscription plugin | Commands renamed to **`/follow` family**; user-facing noun is "follow", not "subscription"; startup collision check |
| B3 — no `TenantContext` at filter time | Filter injects `TenantResolver` and resolves tenant itself (§ WI 2) |
| D1/D1b — cross-tenant delivery can't route; tenant adapter registry unpopulated | **Cross-tenant follow cut from v1** (moved to Deferred with the required data-model change spelled out) |
| D2 — Slack/Discord never deliver `/`-commands as messages | Inbound command UX **scoped to Telegram (+ SMS/email) in v1**; Slack slash-command & Discord app-command registration in Deferred |
| D3 — events don't satisfy sealed `HookEvent` contract | Event shapes now carry `agentId`/`sessionKey`; `"system"` convention for non-agent publishes (§ WI 1d) |
| D4 — tools placed in `jaiclaw-messaging` inverts dependency | All tools live in `jaiclaw-topics`; `jaiclaw-messaging` untouched |
| S1 — any chat member can (un)follow the whole chat | `jaiclaw.topics.commands.allowed-actors` gating + unfollow restricted to follower/admin (§ WI 2) |
| S2 — `topic_publish` LLM tool is an injection megaphone | ToolProfile gating: publish/unfollow-others **excluded from default profiles** (§ WI 5) |
| S3 — publish REST endpoint open in default posture | Explicit authz statement + property-gated web layer (§ WI 4) |
| S4 — unbounded auto-create | Quotas + auto-create **off for follow**, on only for default-topic publish (§ WI 3) |
| O1 — no platform rate-limit awareness | Per-adapter delivery semaphore + honor 429/`Retry-After` with one bounded retry (§ WI 1c) |
| O2 — publish blocks on slowest delivery | `publishAsync(...)` returning `PublishReceipt`; LLM tool + REST use async by default (§ WI 1c) |
| O3/O3b — single-replica storage; jsonfile violates tenancy conformance | Tenant-scoped file layout; single-replica limitation documented loudly; Redis impl named as v1.1 item |
| O4 — no dead-subscriber hygiene | N-strikes auto-unfollow + `purgeSubscriber` + tenant-offboard cascade (§ WI 6.5) |
| O5 — fan-out bypasses `MessageChunker` | Publisher chunks per `adapter.platformLimits()` (§ WI 1c) |
| O6 — no audit/deletion path | `AuditLogger` integration + `purgeSubscriber` for compliance (§ WI 6.5) |
| Transport not abstracted (Kafka question) | New **`TopicDeliveryTransport` SPI** with in-process default; Camel/Kafka drop-in later, mirroring pipeline's `seda:`→`kafka:` pattern (§ WI 1c) |

---

## Context

Right now JaiClaw's channel-messaging surface is one-shot request/response:
an inbound `ChannelMessage` runs the agent, an outbound `ChannelMessage`
goes to a single `(channelId, peerId)`. There is no way for arbitrary
code (an agent, a pipeline stage, an external caller) to say "publish
this to everyone interested in the alerts topic" and have it fan out to
three Telegram chats and a Slack channel.

The only existing "many recipients" primitive is
`MessagingMcpToolProvider.broadcast_message` — a static one-shot
`List<{channelId, peerId}>` batch send. No follow persistence, no
topic model, no way for a Telegram bot user to `/follow alerts`.

**What we're building:** a first-class Topic + Follow layer that sits
alongside the existing channel system. Topics are scoped to a tenant,
followers are identified as `(channelId, accountId, peerId)` triples
(one Telegram chat, one Slack channel, etc.), and publishing fans out
through the existing channel adapters.

Two-way plumbing:
- **Inbound (follow path)** — a `/follow alerts` command arriving via a
  supported channel adapter is intercepted before the agent runs,
  records the follow, and replies to confirm. **v1 supports this on
  Telegram** (bot commands arrive as ordinary message text) and any
  plain-text channel (SMS, email). Slack and Discord require native
  command registration and are deferred (see § Deferred); their users
  are managed via REST/shell/agent-tool in v1.
- **Outbound (publish path)** — anywhere in the runtime,
  `topicPublisher.publish("alerts", ChannelMessage.text(...))` looks up
  all followers for that topic in the current tenant and dispatches
  through the appropriate channel adapters.

**Naming note (important):** the user-facing noun is **follow/follower**,
never "subscription". `jaiclaw-subscription` already means *paid plan
subscriptions* (Stripe/PayPal/Telegram payments), and its Telegram
plugin owns `/subscribe`, `/status`, `/cancel`. Docs, commands, tools,
and class names in this module must not reuse that vocabulary.

**Approved decisions (v2):**
- Topics live **inside** a tenant. `(tenantId, topicId)` is the topic
  primary key. Different tenants have separate topic namespaces.
- **v1 is same-tenant only.** A follower belongs to the tenant whose
  adapter delivered its messages; it can only follow topics in that
  tenant. Cross-tenant follow is deferred (see § Deferred for the data
  model it requires).
- Persistence: SPI with two default impls — in-memory and
  JSON-file-backed — selected by `jaiclaw.topics.storage: memory | jsonfile`.
  **Both defaults are single-replica only** (documented in TOPICS.md and
  the starter README). A Redis-backed impl is the named v1.1 follow-up
  for multi-replica gateways; until then, multi-replica deployments must
  bring their own `TopicRegistry` bean.
- Delivery transport is behind a **`TopicDeliveryTransport` SPI**. v1
  ships the in-process virtual-thread transport only, but the seam is
  defined now so a Camel-backed transport (SEDA locally, Kafka in
  production — exactly the `PipelineRouteBuilder` pattern where the
  endpoint URI is configuration) drops in without reshaping the module.
- **Backward compatibility**: the topic layer is additive. Every
  existing code path that calls `ChannelRegistry.get(...).sendMessage(...)`
  or the existing `broadcast_message` MCP tool keeps working unchanged.
  Inbound messages that are not `/follow`-family commands flow to the
  agent exactly as today. The paid-subscription `/subscribe` flow is
  explicitly regression-tested with topics enabled. See § Work item 7.5.
- **Per-tenant default topic** `"{tenantId}:default"` is auto-created on
  first *publish* only (never on follow — see § WI 3). Zero-config path:
  `publisher.publish(channelMessage)` sends to the current tenant's
  `default` topic. In single-tenant mode this is `"default:default"`.

---

## Work item 0 — Composite gateway filter chain (core + starter)

**This is the prerequisite everything else sits on.** Today
`JaiClawGatewayAutoConfiguration.gatewayLifecycle(...)` takes
`ObjectProvider<GatewayMessageFilter>.getIfAvailable()` — exactly one
filter bean. `TelegramUserIdFilter` is auto-configured whenever
`jaiclaw.channels.telegram.allowed-users` is set, so registering a
second `GatewayMessageFilter` bean today throws
`NoUniqueBeanDefinitionException` at startup.

### 0a. `CompositeGatewayMessageFilter`

New class in `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/`:

```java
public class CompositeGatewayMessageFilter implements GatewayMessageFilter {
    // Filters sorted by Spring's @Order / Ordered; each filter's
    // downstream is the next filter; the last filter's downstream is
    // the GatewayService. A filter that does not forward terminates
    // the chain for that message (existing drop semantics preserved).
}
```

- `GatewayMessageFilter` gains a default `int getOrder()` (default
  `0`); implementations override or use `@Order`.
- **Ordering contract (security-relevant):** authorization and
  rate-limiting filters run **before** feature filters.
  `TelegramUserIdFilter` gets `HIGHEST_PRECEDENCE + 100`;
  `TopicCommandGatewayFilter` gets `0`. This guarantees unauthorized /
  rate-limited users never reach `/follow` handling.
- The existing `setDownstream(ChannelMessageHandler)` chaining pattern
  is kept; the composite wires it at construction.

### 0b. Auto-config change

`JaiClawGatewayAutoConfiguration.gatewayLifecycle(...)` switches from
`getIfAvailable()` to `orderedStream().toList()`:

- 0 filters → plain `GatewayLifecycle` (unchanged).
- 1 filter → `FilteredGatewayLifecycle` with that filter (unchanged
  behavior, no wrapper allocation).
- 2+ filters → `FilteredGatewayLifecycle` with a
  `CompositeGatewayMessageFilter`.

**Regression guarantee:** deployments with only `TelegramUserIdFilter`
(or only a custom filter) see byte-for-byte identical wiring. New Spock
spec `CompositeGatewayMessageFilterSpec` covers ordering, drop
semantics, and the 2-filter boot that crashes today.

---

## Work item 1 — New module `extensions/jaiclaw-topics`

A dedicated extension, opt-in via `jaiclaw.topics.enabled=true`. Package
`io.jaiclaw.topics`.

### 1a. Core types (records)

- `Follower(String channelId, String accountId, String peerId)` —
  the identity of one message endpoint. Fields match
  `ChannelMessage.channelId()/accountId()/peerId()` exactly so
  conversion is trivial. Immutable, equals/hashCode by all fields.
  *(Reserved for cross-tenant later: a `followerTenantId` component —
  see § Deferred. Not present in v1; adding a record component is a
  source-compatible change for users of the static factory.)*
- `TopicFollow(String tenantId, String topicId, Follower follower,
   Instant followedAt, String followedBy)` — one row in the registry.
  `followedBy` is the platform user id / actor who ran `/follow`,
  recorded for audit **and enforced** for unfollow (§ WI 2).
- `TopicKey(String tenantId, String topicId)` — the primary key of a
  topic. Static factory + human-readable wire format `acme:alerts`.
- `TopicMetadata(TopicKey key, Instant createdAt, String createdBy,
   Map<String,String> attributes)` — v1 drops v1-plan's `openPublish`
  and `visible` flags (they only served cross-tenant, which is cut).
  `attributes` is a forward-compat escape hatch.

### 1b. `TopicRegistry` SPI

```java
public interface TopicRegistry {
    TopicFollow follow(TopicKey topic, Follower follower, String actor);
    boolean unfollow(TopicKey topic, Follower follower);
    List<TopicFollow> followersOf(TopicKey topic);
    List<TopicKey> topicsFor(Follower follower);      // within current tenant in v1
    List<TopicKey> topicsInTenant(String tenantId);
    boolean isFollowing(TopicKey topic, Follower follower);
    void createTopic(TopicMetadata metadata);
    boolean deleteTopic(TopicKey topic);
    Optional<TopicMetadata> describeTopic(TopicKey topic);
    int purgeFollower(Follower follower);              // compliance/hygiene (§ 6.5)
    int purgeTenant(String tenantId);                  // tenant offboard cascade (§ 6.5)
}
```

Two shipped implementations:

- `InMemoryTopicRegistry` — `ConcurrentHashMap<TopicKey, Set<Follower>>`
  plus a reverse `ConcurrentHashMap<Follower, Set<TopicKey>>`. Not
  persistent. **All mutating methods are serialized per topic** (compute
  on the CHM entry) so concurrent `/follow`s from virtual threads can't
  interleave lost updates.
- `JsonFileTopicRegistry` — **tenant-scoped layout** per the
  multi-tenancy conformance checklist:
  `~/.jaiclaw/topics/{tenantId}/follows.json` (one file per tenant;
  path root overridable via `jaiclaw.topics.jsonfile.path`). Rewrites
  the affected tenant's file on mutation with the `.tmp`-and-rename
  dance (per-tenant write lock held across read-modify-write —
  the `JsonFileSubscriptionRepository` pattern, hardened). The reverse
  index (`topicsFor`) is rebuilt in memory at load; with same-tenant-only
  follows it never needs cross-tenant reads.
- **Documented limitation** (TOPICS.md + starter README + a WARN log at
  startup when `storage != memory` and more than one instance is
  suspected): both defaults are single-replica. Multi-replica gateways
  need a shared store — `RedisTopicRegistry` is the named v1.1 item;
  until then users declare their own `TopicRegistry` bean
  (`@ConditionalOnMissingBean` gates the defaults).

### 1c. `TopicPublisher` service + `TopicDeliveryTransport` SPI

```java
public interface TopicPublisher {
    // Level 1 — Convenience. Publishes to the CURRENT tenant's default
    // topic. Auto-creates it if absent (the ONLY auto-create path).
    PublishReceipt publish(ChannelMessage template);

    // Level 2 — Explicit topic in the current tenant.
    // TenantContextHolder.get() must match topic.tenantId(),
    // else TenantAccessDeniedException.
    PublishReceipt publish(TopicKey topic, ChannelMessage template);
    PublishReceipt publish(String topicId, String content);           // convenience

    // Level 2b — Filtered fan-out.
    PublishReceipt publish(TopicKey topic, ChannelMessage template,
                           Predicate<Follower> filter);

    // Level 3 — Direct-send bypass: one message, one recipient, no
    // topic lookup — but through the same chunking/throttle/audit path.
    PublishReceipt publishDirect(String channelId, String accountId,
                                 String peerId, String content);

    // Blocking convenience: waits for the receipt's completion future.
    PublishResult publishAndWait(TopicKey topic, ChannelMessage template,
                                 Duration timeout);
}

public record PublishReceipt(String publishId, TopicKey topic,
                             int followerCount,
                             CompletableFuture<PublishResult> completion) {}

public record PublishResult(String publishId, int followerCount,
                            int deliveredCount, int failedCount,
                            List<DeliveryFailure> failures) {}
```

**Async by default (fixes O2):** `publish(...)` enqueues deliveries and
returns immediately with a `PublishReceipt`; final counts arrive on the
`completion` future and via `TopicMessagePublishedEvent`. REST returns
`202 Accepted` with the receipt; the LLM tool returns the receipt
(agents don't block their loop on fan-out). `publishAndWait` exists for
tests and small fan-outs.

**Delivery pipeline, per follower (fixes O1/O5):**
1. Resolve the adapter via `ChannelRegistry.get(follower.channelId())`
   (v1 is same-tenant; the tenant-adapter branch returns when
   cross-tenant lands).
2. **Chunk** the content with `MessageChunker.chunk(content,
   adapter.platformLimits())` — same as `GatewayService.deliverResponse()`.
   Long publishes must not fail where chat replies succeed.
3. Stamp each chunk with the follower's `channelId/accountId/peerId`,
   dispatch through the transport.
4. **Throttle:** a per-`channelId` `Semaphore` (permits from
   `jaiclaw.topics.delivery.max-concurrency-per-channel`, default 4)
   bounds concurrent sends per adapter. On a 429/rate-limit failure the
   delivery honors `Retry-After` (or a default backoff) for **one**
   bounded retry, then records `DeliveryFailure(reason=rate-limited)`.
   This is a floor, not a delivery guarantee — real retry/DLQ arrives
   with the Camel transport (§ Deferred).
5. Wrap every async hop with `TenantContextPropagator.wrap(...)`.
6. Fire per-delivery + summary events (§ 1d) and audit records (§ 6.5).

**`TopicDeliveryTransport` SPI (makes "in-memory today, Kafka later" a
true statement):**

```java
public interface TopicDeliveryTransport {
    void dispatch(PublishTask task);   // task = publishId, topic, follower,
                                       // chunked payloads, result callback
}
```

- v1 ships `InProcessDeliveryTransport` — virtual-thread executor
  (following `HookRunner`'s pattern), the semaphore throttle, and the
  bounded 429 retry, all living behind this interface.
- The follow-up `CamelDeliveryTransport` (jaiclaw-camel extension)
  publishes `PublishTask`s to a Camel endpoint whose URI is pure
  configuration — `seda:topic-delivery` locally,
  `kafka:jaiclaw-topic-delivery` in production — mirroring
  `PipelineRouteBuilder`'s inter-stage transport exactly. Consumers on
  that route perform steps 1–6, giving buffering, retries, DLQ, and
  multi-instance workers with **zero change to `TopicPublisher`
  callers**. Not built in v1; the SPI ships in v1 so it can be.

Failure modes captured in `PublishResult`: `no-such-channel`,
`send-failed`, `rate-limited`.

### 1d. Hook events (contract-compliant)

Six new `HookEvent` sealed-subtype records in
`core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/`. The
`HookEvent` contract requires `agentId()` (never null), `sessionKey()`
(nullable), `timestamp()` on every subtype — v1's shapes omitted the
first two. **Convention:** publishes initiated outside an agent session
(REST, shell, pipeline stage) carry `agentId="system"`,
`sessionKey=null`; the LLM tools pass the live session's agentId +
sessionKey through.

- `TopicCreatedEvent(agentId, sessionKey, timestamp, tenantId, topicId, actor)`
- `TopicDeletedEvent(agentId, sessionKey, timestamp, tenantId, topicId, actor)`
- `TopicFollowedEvent(agentId, sessionKey, timestamp, tenantId, topicId, channelId, accountId, peerId, actor)`
- `TopicUnfollowedEvent(...)` — same fields
- `TopicMessagePublishedEvent(agentId, sessionKey, timestamp, tenantId, topicId, publishId, followerCount, deliveredCount, failedCount)` — fired on completion
- `TopicMessageDeliveredEvent(agentId, sessionKey, timestamp, tenantId, topicId, publishId, channelId, accountId, peerId, failureReason?)` — per delivery

Same factory pattern as the `Pipeline*Event` records; add all six to the
`HookEvent` sealed permits list; fired via the existing `HookRunner` +
`ApplicationEventPublisher` double path (the `PipelineHookFirer`
pattern → new `TopicHookFirer`). Update `HookEventTypesSpec.groovy`.

*(Acknowledged tradeoff, unchanged from the pipeline precedent: every
extension adding events must touch core's sealed permits list. A
follow-up RFC on an open extension-event mechanism is worth filing, but
out of scope here.)*

---

## Work item 2 — Inbound follow/unfollow interceptor (Telegram-first)

New `TopicCommandGatewayFilter` implementing `GatewayMessageFilter`
(order `0` — after auth/rate-limit filters, per § 0a), registered only
when `jaiclaw.topics.enabled=true` **and**
`jaiclaw.topics.interceptor.enabled=true`.

**Channel scoping (fixes D2):** the filter only intercepts messages
whose `channelId` is in `jaiclaw.topics.interceptor.channels` (default:
`telegram, sms, email` — channels where a leading `/` arrives as plain
message text). Slack swallows unregistered slash commands client-side
and Discord routes `/` into its native command picker; neither delivers
`/follow` as a message event, so listing them here would be dead
config. Slack/Discord get native command registration in a follow-up
(§ Deferred); until then their users are managed via REST, shell, or
the agent tools.

**Tenant resolution (fixes B3):** tenant context is set by
`GatewayService.onMessage(...)` — *downstream* of this filter — so the
filter cannot read `TenantContextHolder`. The filter injects the same
`TenantResolver` bean the gateway uses and resolves the tenant from the
inbound message itself (`resolveTenantFromChannel` logic, extracted
into a small shared helper on `TenantResolver` so the logic lives once).
Resolution failure → command rejected with a polite error reply, never
a stack trace.

**Commands (fixes B2 — note: not `/subscribe`):**

- `/follow <topic>` — record the follow for the message's
  `(channelId, accountId, peerId)`. Topic resolves within the current
  tenant. Bare `/follow` follows the tenant's `default` topic. Reply
  confirmation via `TopicPublisher.publishDirect(...)`.
- `/unfollow <topic>` — reverse. **Permitted only when the requesting
  platform user (`followedBy` actor) matches the original follower's
  actor, or the actor is in `allowed-actors`** (below). Prevents any
  group member from silently detaching a channel someone else wired up.
- `/following` — list topics this endpoint follows.
- `/topics` — list topics in the current tenant.

**Actor gating (fixes S1):**
`jaiclaw.topics.commands.allowed-actors` (list of platform user ids;
default empty = **any chat member may follow**, matching v1's openness
for single-tenant hobby deployments, but the property is the documented
first knob for group/business deployments). When non-empty, `/follow`
and `/unfollow` from other users get a "not permitted" reply. TOPICS.md
carries a **"Securing follow commands"** section — this is a
production-checklist item, not fine print.

**Startup collision check:** at startup the filter logs (WARN) if
`jaiclaw-subscription-telegram` is on the classpath, confirming the
verb sets are disjoint (`/follow` family vs `/subscribe`, `/status`,
`/cancel`) — and the Spock coexistence spec (§ 7.5) enforces it so a
future rename in either module can't silently collide.

When a message matches, the filter handles it and does not forward —
the agent doesn't see it, and (deliberately) `MessageReceivedEvent`
doesn't fire for it. Non-command messages pass through unchanged.
Command prefix configurable via `jaiclaw.topics.command-prefix`
(default `/`).

---

## Work item 3 — Topic lifecycle, quotas, auto-create

- **Publish is tenant-scoped.** `TopicPublisher.publish(topic, ...)`
  requires `TenantContextHolder.get()` (or `"default"` in single-tenant
  mode) to match `topic.tenantId()`; otherwise
  `TenantAccessDeniedException`. (With cross-tenant cut, this is the
  entire access-control model — no visibility flags in v1.)
- **Auto-create only on the default-topic publish path** (fixes S4).
  `/follow typo-topic` on a nonexistent topic returns "no such topic —
  try /topics" instead of minting a ghost. Explicit creation happens
  via REST/shell/agent-tool. `jaiclaw.topics.auto-create` gates only
  the `"{tenant}:default"` creation on first Level-1 publish.
- **Quotas** in `TopicsProperties`, enforced in the registry decorator
  so every impl gets them:
  - `jaiclaw.topics.limits.max-topics-per-tenant` (default 100)
  - `jaiclaw.topics.limits.max-followers-per-topic` (default 1000)
  - `jaiclaw.topics.limits.max-follows-per-follower` (default 50)
  Exceeding a quota → typed exception → polite command reply / 409 on
  REST.

---

## Work item 4 — REST endpoints

New `TopicController` under
`extensions/jaiclaw-topics/src/main/java/io/jaiclaw/topics/web/`,
mounted at `/api/topics`, registered only when
`jaiclaw.topics.web.enabled=true` (**default `false`** — fixes S3: the
web surface is a second explicit opt-in, so enabling topics for
in-process publishing doesn't silently open HTTP endpoints):

- `GET /api/topics` — list topics in the current tenant.
- `POST /api/topics` — create a topic.
- `DELETE /api/topics/{topicId}` — delete a topic.
- `GET /api/topics/{topicId}/followers` — list followers.
- `POST /api/topics/{topicId}/followers` — add a follower
  `(channelId, accountId, peerId)` (external systems; chat users go
  through the interceptor).
- `DELETE /api/topics/{topicId}/followers/{channelId}/{accountId}/{peerId}` — remove.
- `POST /api/topics/{topicId}/publish` — publish. Returns `202` +
  `PublishReceipt` (`publishId`, `followerCount`); outcome observable
  via events/`topic watch`.

**Authz statement (explicit, not inherited):** like the pipeline
controllers, in-method auth is delegated to the Spring Security filter
chain — but TOPICS.md and the controller javadoc state plainly: *if
your deployment has no security filter chain, these endpoints are
unauthenticated; `POST /publish` is a broadcast primitive and MUST be
protected in any non-local deployment.* When `jaiclaw-security`'s JWT
setup is active, the documented pattern maps publish/manage to an
operator role and reads to any authenticated principal. CORS follows
the pipeline web conventions.

*(Cross-tenant follow endpoint from v1 is deleted along with the
feature.)*

---

## Work item 5 — LLM tools

All in `jaiclaw-topics` (nothing added to `jaiclaw-messaging` — fixes
D4): native `ToolCallback`s via `TopicTools` factory (mirroring
`PipelineTools`) + `TopicMcpToolProvider` for MCP hosting.

**ToolProfile gating (fixes S2)** — the framework's existing
`isAvailableIn(profile)` mechanism, applied deliberately:

| Tool | Profiles | Rationale |
|---|---|---|
| `topic_list` | default chat profiles | read-only |
| `topic_followers` | default chat profiles | read-only |
| `topic_follow_me` | default chat profiles | follows **the current session's own** `(channelId, accountId, peerId)` only — the "subscribe me" one-call UX; cannot follow arbitrary endpoints |
| `topic_unfollow_me` | default chat profiles | reverse, own endpoint only |
| `topic_follow` (arbitrary endpoint) | **operator/FULL profile only** | can wire any chat to any topic |
| `topic_unfollow` (arbitrary endpoint) | **operator/FULL profile only** | can detach others |
| `topic_publish` | **operator/FULL profile only** | broadcast to every follower — a prompt-injected chat session must never reach this |

The split `*_me` variants are what make "subscribe me to alerts" safe
to hand to a conversational agent: the tool derives the endpoint from
the live session context, and the model cannot aim it elsewhere.

---

## Work item 6 — Shell commands

`apps/jaiclaw-shell-commands/src/main/java/io/jaiclaw/shell/commands/TopicCommand.java`,
matching `PipelineCommand` exactly (auto-detects local
`TopicRegistry`/`TopicPublisher` beans, HTTP fallback via
`jaiclaw.topics.shell.gateway-url`). Hyphenated aliases per the CLI
module pattern (`topic publish` + `topic-publish`):

- `topic list [--tenant <id>]`
- `topic create --topic <id>` / `topic delete --topic <id>`
- `topic followers --topic <id>`
- `topic publish --topic <id> --content "..."` — prints the
  `PublishReceipt`, then (local mode) waits on `completion` and prints
  final counts; `--no-wait` to fire-and-forget.
- `topic follow --topic <id> --channel <id> --account <id> --peer <id>`
- `topic unfollow --topic <id> --channel <id> --account <id> --peer <id>`
- `topic watch --topic <id>` — tails `TopicMessageDeliveredEvent`
  (local `@EventListener` or the pipeline `watch` SSE trick).
- `topic purge-follower --channel <id> --account <id> --peer <id>` —
  compliance/hygiene surface for § 6.5.

---

## Work item 6.5 — Follower hygiene, audit, compliance

**Dead-endpoint eviction (fixes O4):** a
`FollowerHealthTracker` counts consecutive `send-failed` deliveries per
follower. At `jaiclaw.topics.delivery.evict-after-failures` (default
10; `0` disables) the follower is auto-unfollowed from the affected
topic, firing `TopicUnfollowedEvent(actor="system:evicted")` and an
audit record. Rate-limit failures don't count toward eviction.

**Tenant offboard cascade:** `TopicRegistry.purgeTenant(tenantId)` —
documented as the required call when a tenant is deprovisioned (and
wired into the tenant-unregistration path if/when one exists; today
that path is manual).

**Compliance surface (fixes O6):** the registry durably stores chat
identifiers (`peerId`s) and actor ids. `purgeFollower(follower)`
removes every follow + metadata trace for an endpoint (the "delete this
chat's data" answer), surfaced via shell (`topic purge-follower`) and
REST (`DELETE .../followers/...` already covers the per-topic case).
When a `jaiclaw-audit` `AuditLogger` bean is present, follow/unfollow/
create/delete/publish-summary each write an `AuditEvent` in addition to
hook events (mirrors how other modules integrate audit; hook events
alone are fire-and-forget observability, not an audit trail).

---

## Work item 7 — Configuration surface

Root `pom.xml` `<dependencyManagement>` + `jaiclaw-bom`:
- `io.jaiclaw:jaiclaw-topics:${project.version}`
- `io.jaiclaw:jaiclaw-starter-topics:${project.version}` (thin meta-pom)

`application.yml`:

```yaml
jaiclaw:
  topics:
    enabled: false                        # opt-in
    storage: memory                       # memory | jsonfile  (both single-replica; see TOPICS.md)
    jsonfile:
      path: ${user.home}/.jaiclaw/topics  # root dir; per-tenant files underneath
    auto-create: true                     # default-topic auto-create on first publish ONLY
    command-prefix: "/"
    interceptor:
      enabled: true                       # register TopicCommandGatewayFilter
      channels: [telegram, sms, email]    # channels whose messages carry /-commands as text
    commands:
      allowed-actors: []                  # empty = any chat member may /follow (document the risk)
    delivery:
      max-concurrency-per-channel: 4      # per-adapter semaphore permits
      evict-after-failures: 10            # 0 = never auto-evict
    limits:
      max-topics-per-tenant: 100
      max-followers-per-topic: 1000
      max-follows-per-follower: 50
    web:
      enabled: false                      # REST surface is a second explicit opt-in
    shell:
      gateway-url: http://localhost:8080
```

---

## Work item 7.5 — Backward compatibility guarantee

**Non-negotiable invariant:** every existing consumer of the messaging
system keeps working with zero changes. The topic layer is *additive*.

- **`ChannelRegistry.get(channelId).sendMessage(...)`** — unchanged.
  Fires no topic events, doesn't touch the registry.
- **`MessagingMcpToolProvider.broadcast_message`** — unchanged, module
  untouched (v2 adds no tools to `jaiclaw-messaging`).
- **Gateway filter wiring** — deployments with zero or one existing
  `GatewayMessageFilter` bean get identical wiring to today (§ 0b).
  The previously-broken two-filter case now boots with a composite.
- **Paid subscriptions coexistence (the B2 regression):** new Spock
  spec boots `TopicCommandGatewayFilter` + `TelegramSubscriptionPlugin`
  together and asserts: `/subscribe pro` passes through the filter,
  reaches `GatewayService`, fires `MessageReceivedEvent`, and the
  payments plugin responds; `/follow alerts` is intercepted and the
  plugin never sees it. This spec is the permanent guard on the verb
  namespace.
- **Existing inbound flows** — only `/follow`-family commands on
  interceptor-enabled channels are intercepted; everything else reaches
  `GatewayService.onMessage(...)` and the agent runs as before.
- **Existing tests** — `jaiclaw-topics` is opt-in (default `false`); no
  test that doesn't set it experiences any behavior change. The
  auto-config change in § 0b is covered by its own spec plus a re-run
  of the existing gateway auto-configuration specs.

---

## Files to create or modify

**Edited in core/starter (Work item 0):**
- `core/jaiclaw-gateway/.../GatewayMessageFilter.java` — add default `getOrder()`.
- `core/jaiclaw-gateway/.../CompositeGatewayMessageFilter.java` — new.
- `jaiclaw-spring-boot-starter/.../JaiClawGatewayAutoConfiguration.java` —
  `orderedStream()` collection + composite wiring.
- `channels/jaiclaw-channel-telegram/.../TelegramUserIdFilter.java` —
  add order constant (`HIGHEST_PRECEDENCE + 100`).

**New module `extensions/jaiclaw-topics/`:**
- `pom.xml` (follows `jaiclaw-pipeline`'s shape; deps on
  `jaiclaw-channel-api`, `jaiclaw-gateway`, `jaiclaw-core`,
  `spring-boot-autoconfigure`; optional `jaiclaw-audit`)
- `src/main/java/io/jaiclaw/topics/`
  - `TopicKey.java`, `Follower.java`, `TopicFollow.java`, `TopicMetadata.java`
  - `TopicRegistry.java` (SPI), `QuotaEnforcingTopicRegistry.java` (decorator),
    `InMemoryTopicRegistry.java`, `JsonFileTopicRegistry.java`
  - `TopicPublisher.java`, `DefaultTopicPublisher.java`,
    `PublishReceipt.java`, `PublishResult.java`, `DeliveryFailure.java`
  - `transport/TopicDeliveryTransport.java` (SPI),
    `transport/InProcessDeliveryTransport.java`, `transport/PublishTask.java`
  - `TopicHookFirer.java`, `FollowerHealthTracker.java`
  - `TopicCommandParser.java`
  - `filter/TopicCommandGatewayFilter.java`
  - `web/TopicController.java`
  - `tool/TopicListTool.java`, `TopicFollowersTool.java`,
    `TopicFollowMeTool.java`, `TopicUnfollowMeTool.java`,
    `TopicFollowTool.java`, `TopicUnfollowTool.java`,
    `TopicPublishTool.java`, `TopicTools.java` (factory)
  - `mcp/TopicMcpToolProvider.java`
  - `autoconfigure/JaiClawTopicsAutoConfiguration.java`,
    `TopicsProperties.java`, `TopicsWebAutoConfiguration.java`
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**New meta-pom `jaiclaw-starters/jaiclaw-starter-topics/pom.xml`.**

**New hook events under `core/jaiclaw-core/.../hook/event/`:**
- `TopicCreatedEvent.java`, `TopicDeletedEvent.java`,
  `TopicFollowedEvent.java`, `TopicUnfollowedEvent.java`,
  `TopicMessagePublishedEvent.java`, `TopicMessageDeliveredEvent.java`
- Extend `HookEvent.java` sealed `permits` + `HookEventTypesSpec.groovy`.

**New shell command:**
- `apps/jaiclaw-shell-commands/.../TopicCommand.java`
  (+ `jaiclaw-topics` optional dep in its pom)

**Edited:**
- Root `pom.xml`, `extensions/pom.xml`, `jaiclaw-starters/pom.xml`,
  `jaiclaw-bom/pom.xml` — module/managed-dep entries.
- `CLAUDE.md` — new module + count bump + filter-chain note.
- `docs/user/VERSIONS.md` — "New / verified" bullet.
- New `docs/user/TOPICS.md` — concepts, `/follow` flow, **"Securing
  follow commands"** and **"Storage & scaling"** (single-replica
  warning, Redis/BYO-bean, Kafka-transport roadmap) sections, REST/tool/
  shell surfaces.

**Tests (Spock):**
- `CompositeGatewayMessageFilterSpec` — ordering, drop semantics,
  two-filter boot (the v1 crash case).
- `TopicKeySpec`, `FollowerSpec` — value types.
- `InMemoryTopicRegistrySpec`, `JsonFileTopicRegistrySpec` — SPI
  contract + concurrent-mutation safety + per-tenant file layout.
- `QuotaEnforcingTopicRegistrySpec` — all three limits.
- `DefaultTopicPublisherSpec` — fan-out, chunking against a small
  `platformLimits()` stub, filter, failure aggregation, 429 retry path,
  throttle bound, no-followers case, tenant-access-denied, async
  receipt/completion.
- `TopicCommandGatewayFilterSpec` — parsing, channel scoping (Slack
  message passes through untouched), tenant resolution, actor gating,
  unfollow-ownership, pass-through of non-commands.
- `TopicSubscriptionCoexistenceSpec` — the B2 guard (both filters +
  payments plugin; `/subscribe` reaches the plugin, `/follow` doesn't).
- `FollowerHealthTrackerSpec` — N-strikes eviction, rate-limit
  failures excluded.
- `TopicControllerSpec` — REST + tenant scoping + 202 receipt +
  web.enabled=false → no endpoints.
- One spec per tool incl. profile-gating assertions
  (`topic_publish` absent from default profile).
- `TopicMcpToolProviderSpec`, `TopicCommandSpec` (shell, local-vs-HTTP).

---

## Verification

1. **Build:** `./mvnw compile -pl :jaiclaw-gateway,:jaiclaw-spring-boot-starter,:jaiclaw-topics,:jaiclaw-starter-topics,:jaiclaw-shell-commands -am -o` → BUILD SUCCESS.
2. **Unit tests:** `./mvnw test -pl :jaiclaw-gateway,:jaiclaw-topics,:jaiclaw-shell-commands -am -o` → all green.
3. **Filter-chain regression (the v1 boot-crash case):** boot
   gateway-app with `jaiclaw.channels.telegram.allowed-users` set **and**
   `jaiclaw.topics.enabled=true` → context starts, both filters active,
   order verified in logs (auth before topics).
4. **Live smoke (single-tenant, in-memory, Telegram):**
   - Boot with `jaiclaw.topics.enabled=true` and a real Telegram bot.
   - `/follow alerts` from two chats (after `topic create --topic alerts`).
   - `/following` from one → `default:alerts` back.
   - Shell: `bin/jaiclaw topic publish --topic alerts --content "hello"`
     → both chats receive; receipt then final counts printed.
   - `/follow nosuchtopic` → "no such topic" reply, **no ghost topic**
     created (`topic list` unchanged).
5. **Paid-subscription coexistence (B2):** same boot with
   `jaiclaw-subscription-telegram` active: `/subscribe` still returns
   the plans list from the payments plugin; `/follow alerts` is handled
   by topics. Both verified in one session.
6. **Chunking + throttle:** publish a >4096-char message to a topic
   with a Telegram follower → delivered in chunks, no failure. Publish
   to a topic with ~60 stub followers behind an adapter stub that 429s
   → deliveries respect the semaphore, retried once on Retry-After,
   `PublishResult` reconciles counts.
7. **Persistence roundtrip (jsonfile):** follow from a chat, restart
   the JVM, publish → chat still receives; on-disk layout is
   `topics/{tenantId}/follows.json`.
8. **Actor gating:** set `allowed-actors` to one user id; `/follow`
   from another group member → "not permitted" reply, no registry change.
9. **LLM tool smoke:** in a chat session, "follow acme alerts for me" →
   agent calls `topic_follow_me`, session's own endpoint gains the
   follow. Verify `topic_publish` is **not** callable from the default
   chat profile (tool absent from the session's tool list).
10. **MCP tool smoke:** `curl http://localhost:8080/mcp/topics` lists
    the seven tools; `topic_publish` via MCP (operator context) succeeds.
11. **Hook events + audit:** test `@EventListener` sees all six event
    types with `agentId`/`sessionKey` populated per convention; with an
    `InMemoryAuditLogger` bean, follow/publish produce audit records.
12. **Eviction:** stub adapter that always fails → after 10 publishes
    the follower is auto-unfollowed, `TopicUnfollowedEvent(actor=
    "system:evicted")` fires.
13. **Backward-compat regression:** existing `MessagingMcpToolProviderSpec`
    + channel-adapter specs pass with topics disabled (default) and
    enabled. `broadcast_message` response shape unchanged.
14. **Default-topic path:** with topics enabled and none configured,
    `topicPublisher.publish(ChannelMessage.text("hi"))` →
    `followerCount=0`, `TopicCreatedEvent` for `default:default`; add a
    stub follower, republish → `deliveredCount=1`.
15. **e2e:** add `Scenario 7 — Topic pub/sub` to
    `.claude/skills/e2e-test/`: boot with a stubbed follower, `/follow`,
    publish, assert the delivery event fires; plus the coexistence check
    from step 5.

---

## Deferred (later increments, explicit non-goals for this cut)

- **Cross-tenant follow.** Cut from v1 because it cannot route with the
  current data model: delivery resolves adapters by the *publishing*
  tenant, but a follower in another tenant lives behind that tenant's
  own bot/workspace adapter. Bringing it back requires, in order:
  (1) a real tenant-adapter provisioning path — today nothing in
  production code populates `TenantChannelAdapterRegistry`
  (`registerAdapter`/`startTenant` have no callers); (2) `Follower`
  gains a `followerTenantId` component; (3) the publisher routes *per
  follower* via `TenantChannelAdapterRegistry.getAdapter(
  follower.followerTenantId(), follower.channelId())` with per-delivery
  `TenantContext` switching; (4) the visibility/`open-publish` metadata
  flags and the cross-tenant REST endpoint return. None of the v1
  surfaces change shape when this lands.
- **Slack & Discord native commands.** Slack requires slash-command
  registration in the app manifest + `slash_commands` envelope handling
  in `SlackAdapter`; Discord requires application-command registration +
  routing through the existing interaction path in `DiscordAdapter`.
  Both are adapter-level work items independent of the topic layer;
  the interceptor's channel-scoping config already leaves room for them.
- **Camel/Kafka delivery transport.** `CamelDeliveryTransport`
  implementing `TopicDeliveryTransport` (§ 1c), with the endpoint URI as
  configuration (`seda:` locally, `kafka:` in production) — the
  `PipelineRouteBuilder` inter-stage pattern. Brings buffering, real
  retries, DLQ, and multi-instance delivery workers. The v1 SPI is
  shaped so this is additive.
- **`RedisTopicRegistry` (v1.1).** The shipped answer for multi-replica
  gateways; until then, single-replica limitation is documented and
  BYO-bean is the escape hatch.
- **Delivery retries / DLQ beyond the bounded 429 retry.** Arrives with
  the Camel transport.
- **Message templating.** `publish(topic, template)` treats content as
  literal text. Per-follower interpolation (`Hi {{peerName}}`) and
  per-platform formatting (Slack mrkdwn vs Telegram MarkdownV2) are
  follow-ups; v1 ships plain text + chunking only.
- **Filtered follows.** Follow with a predicate ("only
  `severity=high`"). Add via `metadata.tags` on the
  `publish(...filter)` overload if demand appears.
- **Open extension-event mechanism.** RFC to stop every extension from
  editing core's sealed `HookEvent` permits list; topics follows the
  pipeline precedent for now.
