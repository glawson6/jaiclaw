# Topics / Pub-Sub Layer — Implementation Plan

> **Status:** BACKLOG. Approved design, not yet scheduled. Filed
> 2026-07-16. Estimated effort: comparable to `jaiclaw-pipeline` when
> it shipped (~2–3 days of focused work for the base module, another
> ~1 day for shell/MCP surfaces + docs).
>
> **Feature summary:** first-class Topic + Subscription layer letting
> arbitrary code fan a single message out to many
> `(channelId, peerId)` subscribers across Slack, Telegram, Discord,
> etc. Two-way: users can `/subscribe topic` from a chat, and any
> publisher can push to that topic.
>
> **Sibling plans:** matches the shape of
> [`KANBAN-IMPLEMENTATION-PLAN.md`](KANBAN-IMPLEMENTATION-PLAN.md),
> [`OAUTH-IMPLEMENTATION-PLAN.md`](OAUTH-IMPLEMENTATION-PLAN.md),
> [`PIPELINE-STRATEGY.md`](pipeline/PIPELINE-STRATEGY.md), and
> [`COMPLIANCE-IMPLEMENTATION-PLAN.md`](COMPLIANCE-IMPLEMENTATION-PLAN.md).

---

## Context

Right now JaiClaw's channel-messaging surface is one-shot request/response:
an inbound `ChannelMessage` runs the agent, an outbound `ChannelMessage`
goes to a single `(channelId, peerId)`. There is no way for arbitrary
code (an agent, a pipeline stage, an external caller) to say "publish
this to everyone interested in tenant acme's alerts topic" and have it
fan out to a Slack channel, three Telegram chats, and a Discord channel.

The only existing "many recipients" primitive is
`MessagingMcpToolProvider.broadcast_message` — a static one-shot
`List<{channelId, peerId}>` batch send. No subscription persistence, no
topic model, no way for a Telegram bot user to `/subscribe alerts`.

**What we're building:** a first-class Topic + Subscription layer that
sits alongside the existing channel system. Topics are scoped to a
tenant (owned by the tenant that publishes them), subscribers are
identified as `(channelId, peerId)` pairs (one Slack channel, one
Telegram chat, one Discord channel, etc.), and a single subscriber can
subscribe to topics owned by *multiple different tenants* — a bot's
chat can follow `acme:alerts` and `contoso:releases` at the same time.

Two-way plumbing:
- **Inbound (subscribe path)** — a `/subscribe acme:alerts` command
  arriving via any channel adapter gets intercepted before the agent
  runs, records the subscription, and replies to confirm.
- **Outbound (publish path)** — anywhere in the runtime,
  `messagePublisher.publish("acme", "alerts", ChannelMessage.text(...))`
  looks up all subscribers for that `(tenant, topic)` pair and dispatches
  through the appropriate channel adapters.

**Answering the architectural question directly: yes, this is
feasible.** The identity chain
`(agentId, channelId, accountId, peerId)` already uniquely identifies
"one Slack channel in one workspace" or "one Telegram chat", so
subscription keys reuse it verbatim. What's missing is (a) the topic
registry itself, (b) a publisher primitive, (c) the inbound
subscribe/unsubscribe interceptor.

**Approved decisions:**
- Topics live **inside** a tenant. `(tenantId, topicId)` is the topic
  primary key. Different tenants have separate topic namespaces.
- Subscribers can cross tenants — one `(channelId, peerId)` can
  subscribe to topics owned by many tenants.
- Multi-workspace-per-tenant Slack/Telegram is out of scope for now
  (works fine because a single Slack workspace has many *channels*,
  a single Telegram bot serves many *chats*, and those are the
  subscribers we're addressing).
- Persistence: SPI with two default impls — in-memory and
  JSON-file-backed. Which is active is driven by
  `jaiclaw.topics.storage: memory | jsonfile`.
- **Backward compatibility**: the topic layer is <b>additive</b>. Every
  existing code path that calls `ChannelRegistry.get(...).sendMessage(...)`
  or the existing `broadcast_message` MCP tool keeps working
  unchanged. Inbound messages that are not `/subscribe`-family commands
  flow to the agent exactly as today; no topic is auto-created, no
  publish happens implicitly. See § Work item 7.5.
- **Per-tenant default topic** `"{tenantId}:default"` is auto-created on
  first publish/subscribe (when `jaiclaw.topics.auto-create=true`, which
  is the default). This gives new topic-aware callers a zero-config
  path: `publisher.publish(channelMessage)` sends to the current
  tenant's `default` topic. In single-tenant mode this is
  `"default:default"`. Subscribers can `/subscribe default` (no tenant
  prefix) and get their tenant's default. Explicit topic names are
  required only when there are multiple topics.

---

## Work item 1 — New module `extensions/jaiclaw-topics`

A dedicated extension, opt-in via `jaiclaw.topics.enabled=true`. Package
`io.jaiclaw.topics`.

### 1a. Core types (records)

- `Subscriber(String channelId, String accountId, String peerId)` —
  the identity of one message endpoint. Fields match
  `ChannelMessage.channelId()/accountId()/peerId()` exactly so
  conversion is trivial. Immutable, equals/hashCode by all fields.
- `TopicSubscription(String tenantId, String topicId,
   Subscriber subscriber, Instant subscribedAt, String subscribedBy)` —
  one row in the registry. `subscribedBy` is the user id / actor who
  ran `/subscribe` for auditability.
- `TopicKey(String tenantId, String topicId)` — the primary key of a
  topic. Static factory + human-readable format
  `acme:alerts` for wire encoding.

### 1b. `TopicRegistry` SPI

```java
public interface TopicRegistry {
    TopicSubscription subscribe(TopicKey topic, Subscriber subscriber, String actor);
    boolean unsubscribe(TopicKey topic, Subscriber subscriber);
    List<TopicSubscription> subscribersOf(TopicKey topic);
    List<TopicKey> topicsFor(Subscriber subscriber);          // cross-tenant lookup
    List<TopicKey> topicsInTenant(String tenantId);
    boolean isSubscribed(TopicKey topic, Subscriber subscriber);
}
```

Two shipped implementations:

- `InMemoryTopicRegistry` — `ConcurrentHashMap<TopicKey, Set<Subscriber>>`
  plus a reverse `ConcurrentHashMap<Subscriber, Set<TopicKey>>` for the
  `topicsFor(subscriber)` query. Not persistent; subscriptions vanish on
  restart. Matches the "in-memory" storage mode.
- `JsonFileTopicRegistry` — `~/.jaiclaw/topics/subscriptions.json`
  (path overridable via `jaiclaw.topics.jsonfile.path`). Rewrites on
  every mutation with a `.tmp`-and-rename dance. Follows the exact
  pattern of `JsonFileSubscriptionRepository` at
  `extensions/jaiclaw-subscription/src/main/java/io/jaiclaw/subscription/repository/JsonFileSubscriptionRepository.java`.

The auto-config picks one based on `jaiclaw.topics.storage: memory |
jsonfile` (default `memory`). Users bring their own bean (Redis, JPA,
etc.) by declaring an alternative `TopicRegistry` bean —
`@ConditionalOnMissingBean` gates the defaults.

### 1c. `MessagePublisher` service

Three levels of API — most callers use the highest-level one:

```java
public interface MessagePublisher {
    // Level 1 — Convenience. Publishes to the CURRENT tenant's default
    // topic ("{currentTenant}:default"). Auto-creates the topic if it
    // doesn't exist and auto-create is enabled (default). Zero-config
    // path for new consumers that "just want to publish."
    PublishResult publish(ChannelMessage template);

    // Level 2 — Explicit. Publishes to a named topic in a named tenant.
    // Requires TenantContextHolder.get() to match topic.tenantId() OR
    // the topic to have open-publish=true.
    PublishResult publish(TopicKey topic, ChannelMessage template);
    PublishResult publish(String tenantId, String topicId, String content);   // convenience

    // Level 2b — Filtered. Same as Level 2 but skips subscribers where
    // the predicate returns false.
    PublishResult publish(TopicKey topic, ChannelMessage template,
                          Predicate<Subscriber> filter);

    // Level 3 — Direct-send bypass. Same behavior as calling
    // ChannelRegistry.get(channelId).sendMessage(...) but flows through
    // the same audit/hook path as topic publishes. No topic lookup, no
    // fan-out — one message to one recipient. Use when you already know
    // exactly who to send to and don't want the topic system involved.
    PublishResult publishDirect(String channelId, String accountId,
                                 String peerId, String content);
}

public record PublishResult(int subscriberCount, int deliveredCount,
                            int failedCount, List<DeliveryFailure> failures) {}
```

**Default-topic auto-creation semantics** (Level 1 path):
1. `publish(template)` resolves `tenantId` from `TenantContextHolder.get()`
   (or `"default"` in single-tenant mode).
2. Builds `TopicKey("{tenantId}", "default")`.
3. If the topic doesn't exist AND `jaiclaw.topics.auto-create=true`,
   creates it with `TopicMetadata(openPublish=false, visible=false,
   createdBy="system")`. Fires `TopicCreatedEvent`.
4. Delegates to `publish(TopicKey, template)`.
5. If the topic has zero subscribers, `PublishResult.subscriberCount=0`
   and no delivery occurs — this is fine (matches "post to an unmanned
   channel"; caller can log or ignore).

Behavior:
1. Look up subscribers via `TopicRegistry.subscribersOf(topic)`.
2. For each subscriber, clone `template` with the subscriber's
   `channelId`/`accountId`/`peerId` stamped on it.
3. Resolve the adapter via existing `ChannelRegistry.get(channelId)` and
   dispatch. Multi-tenant deployments use the tenant-aware
   `TenantChannelAdapterRegistry.getAdapter(tenantId, channelId)`
   already wired in `GatewayService.deliverResponse()`.
4. Fires typed events (see § 1d) per delivery + one summary event.
5. Wraps async delivery with `TenantContextPropagator.wrap(...)` so
   tenant context propagates to virtual threads.

Delivery runs on virtual threads (following `HookRunner`'s pattern).
Total wall-clock ≈ slowest single delivery, not sum.

Two failure modes captured in `PublishResult`:
- `no-such-channel` — the subscriber's `channelId` isn't registered.
- `send-failed` — the adapter threw during dispatch.

### 1d. Hook events

Six new `HookEvent` sealed-subtype records in
`core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/`:

- `TopicCreatedEvent(tenantId, topicId, actor, timestamp)`
- `TopicDeletedEvent(tenantId, topicId, actor, timestamp)`
- `SubscriptionCreatedEvent(tenantId, topicId, channelId, accountId, peerId, actor, timestamp)`
- `SubscriptionDeletedEvent(...)` — same fields
- `TopicMessagePublishedEvent(tenantId, topicId, subscriberCount, deliveredCount, failedCount, timestamp)`
- `TopicMessageDeliveredEvent(tenantId, topicId, channelId, accountId, peerId, deliveredAt, failureReason?)`

Same shape + factory pattern as the pipeline events shipped in the
previous increment (see the `Pipeline*Event` records under
`core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/`). Add all
six to the `HookEvent` sealed permits list. Fired via the existing
`HookRunner` + `ApplicationEventPublisher` double path — same as
`PipelineHookFirer` — so SSE / observability plugins can subscribe
without new SPIs.

---

## Work item 2 — Inbound subscribe/unsubscribe interceptor

New `TopicCommandGatewayFilter` implementing the existing
`GatewayMessageFilter` SPI at
`core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayMessageFilter.java`.
Sits between the channel adapter and `GatewayService`, matching how
`TelegramUserIdFilter` at
`channels/jaiclaw-channel-telegram/src/main/java/io/jaiclaw/channel/telegram/TelegramUserIdFilter.java`
already does.

Intercepts inbound `ChannelMessage` where `content.trim()` starts with
one of the four opt-in commands. **Topic argument accepts two forms:**
`<tenant>:<topic>` for explicit cross-tenant references, or bare
`<topic>` which resolves against the current tenant (or `default` tenant
in single-tenant mode). Bare `default` (or no argument at all)
subscribes to the current tenant's default topic.

- `/subscribe [<tenant>:]<topic>` — record subscription for the
  message's `(channelId, accountId, peerId)`. Reply confirmation via
  `MessagePublisher.publishDirect(...)`.
- `/unsubscribe [<tenant>:]<topic>` — same, in reverse.
- `/subscriptions` — list all `TopicKey`s this subscriber follows across
  all tenants.
- `/topics [<tenant>]` — list topics in the given tenant (or the current
  tenant if omitted) this subscriber can subscribe to (respects
  tenant-visibility settings — see § 3).

When a message matches, the filter handles it and returns without
forwarding to `GatewayService` — the agent doesn't see it. Non-command
messages pass through unchanged, so the agent still runs for regular
chat traffic (§ Work item 7.5 covers this backward-compat guarantee in
detail).

Command prefix is configurable via `jaiclaw.topics.command-prefix`
(default `/`), so deployments running alongside another bot's commands
can pick a different prefix. Setting
`jaiclaw.topics.interceptor.enabled=false` disables the filter
entirely — deployments that don't want the `/subscribe` UX (e.g., they
manage subscriptions purely via REST) can turn it off.

---

## Work item 3 — Access control on topics

A topic is "owned" by the tenant that publishes it. But a subscriber
(e.g., a Slack channel in workspace X) may follow topics owned by
several different tenants. Two guardrails:

- **Publish is tenant-scoped.** `MessagePublisher.publish(topic, ...)`
  requires the calling code's `TenantContextHolder.get()` to match
  `topic.tenantId()` OR the topic must be flagged `open-publish=true`.
  Otherwise throws `TenantAccessDeniedException`.
- **Subscribe requires topic-visibility.** A subscriber attempting
  `/subscribe acme:alerts` only succeeds if `acme` marked that topic as
  `visible-to-external-subscribers=true`. Config-driven per topic — no
  auth handshake needed (subscribers on the Telegram/Slack side are
  already gated by the platform's own auth).

Topic metadata lives in the same `TopicRegistry` — new record
`TopicMetadata(TopicKey key, boolean openPublish, boolean visible,
Instant createdAt, String createdBy)`. Registry gets
`createTopic(TopicMetadata)` / `deleteTopic(TopicKey)` /
`describeTopic(TopicKey)`. Topics are also implicitly created on first
subscribe when `jaiclaw.topics.auto-create=true`.

---

## Work item 4 — REST endpoints

New `TopicController` under
`extensions/jaiclaw-topics/src/main/java/io/jaiclaw/topics/web/`,
mounted at `/api/topics`:

- `GET /api/topics` — list topics in the current tenant.
- `POST /api/topics` — create a topic (body: `TopicMetadata`).
- `DELETE /api/topics/{topicId}` — delete a topic.
- `GET /api/topics/{topicId}/subscribers` — list subscribers.
- `POST /api/topics/{topicId}/subscribers` — subscribe a
  `(channelId, accountId, peerId)` (used by external systems, not
  Telegram users; those go through the interceptor).
- `DELETE /api/topics/{topicId}/subscribers/{channelId}/{accountId}/{peerId}` — unsubscribe.
- `POST /api/topics/{topicId}/publish` — publish a message (body:
  `{content, ...}`). Returns `PublishResult`.
- `POST /api/topics/cross-tenant/subscribe` — a subscriber joining a
  topic owned by a *different* tenant. Body carries an explicit
  `tenantId` field so cross-tenant subscription requests are unambiguous
  (respects the visibility flag from § 3).

CORS + auth follow the existing controller conventions in
`extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/web/`.

---

## Work item 5 — LLM tools

Add to `MessagingMcpToolProvider` at
`extensions/jaiclaw-messaging/src/main/java/io/jaiclaw/messaging/mcp/MessagingMcpToolProvider.java`
and expose as native `ToolCallback`s via a new `TopicTools` factory
(mirroring `PipelineTools`):

- `topic_list` — list topics in the current tenant.
- `topic_subscribers` — list subscribers of a topic.
- `topic_publish` — publish a message. Args: `topicId`, `content`,
  optional `filter` (JSON with `channelId` allowlist).
- `topic_subscribe` — subscribe a `(channelId, accountId, peerId)`.
- `topic_unsubscribe` — reverse.

These make it trivial for an agent chatting with a user to say
"subscribe you to alerts" and have it just work — the agent already has
the current session's channel/account/peer, so subscribing "me" is a
one-tool-call operation.

---

## Work item 6 — Shell commands

Add to `PipelineCommand`'s neighbor —
`apps/jaiclaw-shell-commands/src/main/java/io/jaiclaw/shell/commands/TopicCommand.java`,
matching the exact pattern of `PipelineCommand` (auto-detects local
`TopicRegistry`/`MessagePublisher` beans, HTTP fallback via
`jaiclaw.topics.shell.gateway-url`):

- `topic list [--tenant <id>]`
- `topic subscribers --topic <id>`
- `topic publish --topic <id> --content "..."`
- `topic subscribe --topic <tenant:id> --channel <id> --account <id> --peer <id>`
- `topic unsubscribe --topic <tenant:id> --channel <id> --account <id> --peer <id>`
- `topic watch --topic <id>` — tails the `TopicMessageDeliveredEvent`
  stream (either via a local `@EventListener` or the same SSE trick the
  pipeline `watch` uses).

---

## Work item 7 — Configuration surface

Add to root `pom.xml` `<dependencyManagement>`:
- `io.jaiclaw:jaiclaw-topics:${project.version}`
- `io.jaiclaw:jaiclaw-starter-topics:${project.version}` (thin meta-pom)

`application.yml`:

```yaml
jaiclaw:
  topics:
    enabled: false                        # opt-in
    storage: memory                       # memory | jsonfile
    jsonfile:
      path: ${user.home}/.jaiclaw/topics/subscriptions.json
    auto-create: true                     # subscribe implicitly creates missing topics
    command-prefix: "/"                   # /subscribe, /unsubscribe, etc.
    interceptor:
      enabled: true                       # register TopicCommandGatewayFilter
    shell:
      gateway-url: http://localhost:8080
    defaults:
      open-publish: false                 # topics require same-tenant to publish by default
      visible-to-external-subscribers: false  # cross-tenant subscribe forbidden by default
```

---

## Work item 7.5 — Backward compatibility guarantee

**Non-negotiable invariant:** every existing consumer of the messaging
system keeps working with zero changes. The topic layer is *additive*.

Concretely:

- **`ChannelRegistry.get(channelId).sendMessage(...)`** — unchanged.
  Fires no topic events, doesn't touch the registry. Still the direct
  path for adapters + any custom code holding a `ChannelRegistry` bean.
- **`MessagingMcpToolProvider.broadcast_message`** — unchanged. Its
  static `List<{channelId, peerId}>` shape is preserved. Internally it
  still iterates the recipient list and calls
  `ChannelRegistry.get(...).sendMessage(...)` per recipient.
  Deployments that only use `broadcast_message` don't need topics
  enabled at all.
- **Existing inbound message flows** — unchanged. When
  `jaiclaw.topics.enabled=true` and the interceptor is registered,
  only messages whose content starts with the topic command prefix
  (`/subscribe`, `/unsubscribe`, `/subscriptions`, `/topics`) are
  intercepted. Everything else passes through to
  `GatewayService.onMessage(...)` and the agent runs as before.
- **Existing tests + integration paths** — unchanged. `jaiclaw-topics`
  is opt-in via `jaiclaw.topics.enabled=true` (default `false`); no
  test whose config doesn't set it experiences any behavior change.

The observability story is unified: `MessagePublisher.publishDirect(...)`
fires the same `TopicMessageDeliveredEvent` as topic-fanned deliveries,
so consumers migrating from direct-send to publisher get consistent
audit/hook coverage. But adopting the publisher is optional — the
direct-send path (ChannelRegistry → adapter) fires no topic events (it
just fires whatever the adapter fires today, which is unchanged).

---

## Files to create or modify

**New module `extensions/jaiclaw-topics/`:**
- `pom.xml` (follows `jaiclaw-pipeline`'s shape; deps on
  `jaiclaw-channel-api`, `jaiclaw-gateway`, `jaiclaw-core`,
  `spring-boot-autoconfigure`, `jsoup` test-scope)
- `src/main/java/io/jaiclaw/topics/`
  - `TopicKey.java`, `Subscriber.java`, `TopicSubscription.java`,
    `TopicMetadata.java`
  - `TopicRegistry.java` (SPI), `InMemoryTopicRegistry.java`,
    `JsonFileTopicRegistry.java`
  - `MessagePublisher.java` (interface), `DefaultMessagePublisher.java`,
    `PublishResult.java`, `DeliveryFailure.java`
  - `TopicCommandParser.java` (parses `/subscribe ...` etc.)
  - `filter/TopicCommandGatewayFilter.java`
  - `web/TopicController.java`
  - `tool/TopicListTool.java`, `TopicPublishTool.java`,
    `TopicSubscribeTool.java`, `TopicUnsubscribeTool.java`,
    `TopicSubscribersTool.java`, `TopicTools.java` (factory)
  - `mcp/TopicMcpToolProvider.java`
  - `autoconfigure/JaiClawTopicsAutoConfiguration.java`,
    `TopicsProperties.java`, `TopicsWebAutoConfiguration.java`
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**New meta-pom `jaiclaw-starters/jaiclaw-starter-topics/pom.xml`.**

**New hook events under `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/`:**
- `TopicCreatedEvent.java`, `TopicDeletedEvent.java`,
  `SubscriptionCreatedEvent.java`, `SubscriptionDeletedEvent.java`,
  `TopicMessagePublishedEvent.java`, `TopicMessageDeliveredEvent.java`
- Extend `HookEvent.java` sealed `permits` list + update
  `HookEventTypesSpec.groovy`.

**New shell command:**
- `apps/jaiclaw-shell-commands/src/main/java/io/jaiclaw/shell/commands/TopicCommand.java`

**Edited:**
- Root `pom.xml` — 2 new managed deps.
- `extensions/pom.xml` — new module.
- `jaiclaw-starters/pom.xml` — new module.
- `jaiclaw-bom/pom.xml` — 2 new managed deps.
- `apps/jaiclaw-shell-commands/pom.xml` — add `jaiclaw-topics` optional.
- `CLAUDE.md` — new module description + module count bump.
- `docs/user/VERSIONS.md` — "New / verified" bullet under the current
  SNAPSHOT.
- New `docs/user/TOPICS.md` — user guide covering
  concepts + `/subscribe` command flow + REST/tool/shell surfaces + cross-tenant scenarios.

**Tests (Spock, matching existing patterns):**
- `TopicKeySpec`, `SubscriberSpec` — value type equality.
- `InMemoryTopicRegistrySpec`, `JsonFileTopicRegistrySpec` — SPI contract.
- `DefaultMessagePublisherSpec` — fan-out, filter, failure aggregation,
  no-subscribers case, tenant-access-denied case.
- `TopicCommandGatewayFilterSpec` — parses commands correctly; passes
  through non-commands; replies via `MessagePublisher`.
- `TopicControllerSpec` — REST + tenant scoping + cross-tenant path.
- `TopicListToolSpec` / `TopicPublishToolSpec` / etc. — one per tool.
- `TopicMcpToolProviderSpec` — MCP dispatch.
- `TopicCommandSpec` — shell command output shape + local-vs-HTTP fallback.

---

## Verification

1. **Build:** `./mvnw compile -pl :jaiclaw-topics,:jaiclaw-starter-topics,:jaiclaw-shell-commands -am -o` → BUILD SUCCESS.
2. **Unit tests:** `./mvnw test -pl :jaiclaw-topics,:jaiclaw-shell-commands -am -o` → all Spock specs green.
3. **Live smoke (single-tenant, in-memory storage):**
   - Boot `jaiclaw-shell` with `jaiclaw.topics.enabled=true`,
     `jaiclaw.channels.telegram.bot-token=...`, and a real Telegram bot.
   - Send `/subscribe default:alerts` to the bot from two different
     Telegram chats.
   - Send `/subscriptions` from one of them — expect
     `default:alerts` back.
   - From the shell: `bin/jaiclaw topic publish --topic default:alerts
     --content "hello"` — both Telegram chats receive "hello".
   - From the shell: `bin/jaiclaw topic subscribers --topic
     default:alerts` — expect 2 rows with the right chat ids.
4. **Live smoke (multi-tenant + cross-tenant subscribe):**
   - Config: two tenants `acme` + `contoso`, each with a Slack workspace.
   - Curl `POST /api/topics/cross-tenant/subscribe` with a Slack channel
     as subscriber and `tenantId=acme, topicId=alerts` — 200 if
     `acme:alerts` has `visible-to-external-subscribers=true`, 403
     otherwise.
   - Publish to `acme:alerts` from acme's tenant context — the
     cross-tenant subscriber receives.
5. **Persistence roundtrip (jsonfile storage):**
   - Set `jaiclaw.topics.storage=jsonfile`.
   - Subscribe from a chat, restart the JVM.
   - Publish — the chat still receives (subscription survived restart).
6. **LLM tool smoke:** in a chat session, "subscribe me to acme
   alerts" — expect the agent to call `topic_subscribe` with the
   current session's `channelId`/`accountId`/`peerId` filled in, and
   the subscriber gains the topic on the next publish.
7. **MCP tool smoke:** `curl http://localhost:8080/mcp/topics` returns
   the five topic tools. A `topic_publish` call succeeds against a real
   subscriber.
8. **Hook event surface:** subscribe a `@EventListener` in a test
   application, verify all six event types fire on the appropriate
   actions.
9. **Backward-compat regression:**
   - Run the existing `MessagingMcpToolProviderSpec` and channel-adapter
     specs with `jaiclaw.topics.enabled=false` (default) — must pass
     unchanged.
   - Run them again with `jaiclaw.topics.enabled=true` — must still
     pass. The topic layer must not interfere with the direct-send
     path.
   - `curl -X POST /api/messaging/broadcast_message` — unchanged
     response shape.
10. **Default-topic path:** with `jaiclaw.topics.enabled=true` and no
    explicit topics configured, call
    `messagePublisher.publish(ChannelMessage.text("hi"))` from a test
    — `PublishResult.subscriberCount=0` (nobody's subscribed), and a
    `TopicCreatedEvent` fired for `default:default`. Then subscribe a
    stubbed subscriber and republish — `deliveredCount=1`.
11. **e2e:** add `Scenario 7 — Topic pub/sub` to
    `.claude/skills/e2e-test/`: boot pipeline-e2e + a stubbed
    subscriber, `/subscribe`, publish, assert the delivery event fires.

---

## Deferred (later increments, explicit non-goals for this cut)

- **Multi-workspace/multi-bot per tenant.** Today one tenant = one
  Slack workspace = one Telegram bot. That already permits many
  *channels* + many *chats* as subscribers. Adding multiple workspaces
  per tenant is a separate refactor (config records grow lists,
  `ChannelRegistry` becomes `Map<channelId, List<Adapter>>`, adapters
  need an `instanceId` on outbound routing). Do it if/when it becomes
  a real need; the topic layer we're building here won't need to change.
- **Cross-tenant publish.** A topic is publishable only by its owning
  tenant. If cross-tenant publish becomes desirable, add a
  `co-publishers[]` list on `TopicMetadata`.
- **Delivery retries / DLQ.** First failure is captured in
  `PublishResult.failures`; no automatic retry. If needed, add a
  Camel-backed transport option in a follow-up (mirrors
  `jaiclaw-pipeline`'s SEDA/Kafka transport shape).
- **Message templating.** `publish(topic, template)` treats `template`
  as literal text. Interpolating per-subscriber (e.g.
  `Hi {{peerName}}`) is a follow-up if there's demand.
- **Filtered subscriptions.** Subscribe with a predicate ("only
  messages tagged `severity=high`"). Not in v1; add via a
  `metadata.tags` filter on the `publish(...filter)` overload if
  needed.
