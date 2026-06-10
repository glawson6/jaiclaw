# Migrating to JaiClaw 0.8.0

> **Status:** in-flight. The 0.8.0 release is the **hard-break** release in
> which Phase 3 (`docs/CODEBASE-ANALYSIS-2026-06-10.md` §3, §4.5) lands. This
> guide is written incrementally as each Phase 3 PR ships; expect more
> sections to appear as 0.8.0 takes shape on the `main` branch.

## Why 0.8.0 is a hard break

JaiClaw 0.7.x deferred several API ergonomics decisions
(`CODEBASE-ANALYSIS-2026-06-10.md` Phase 3). 0.8.0 lands them all in a
single release rather than deprecating-then-removing across two minors:

- Faster path to a 1.0 stability story (`docs/user/ROAD-TO-1.0.md`).
- Each Phase 3 change individually is small, but a deprecation window for
  each would mean a six-month tail of double-coded APIs.
- The early-adopter audience for 0.7.x is small enough that one
  migration pass is less painful than living with deprecations.

You will need to make code changes against this document when upgrading
from 0.7.x. The sections below are organized by Phase 3 PR; each names
the diff shape for the breaking change.

---

## P3.1 — Typed hook events (LANDED)

**What changed.** The plugin hook system migrated from a `HookName`
enum + `HookHandler<E, C>` pair to a sealed `HookEvent` hierarchy +
`HookHandler<E extends HookEvent>`. Dispatch is keyed by the event's
runtime class rather than the enum discriminator.

The 16 events (`AgentStartedEvent`, `AgentEndedEvent`, `LlmInputEvent`,
`LlmOutputEvent`, `BeforePromptBuildEvent`, `ToolCallStartedEvent`,
`ToolCallEndedEvent`, `BeforeCompactionEvent`, `AfterCompactionEvent`,
`SessionStartedEvent`, `SessionEndedEvent`, `BeforeResetEvent`,
`MessageReceivedEvent`, `MessageSendingEvent`, `MessageSentEvent`,
`BeforeModelResolveEvent`) live under
`io.jaiclaw.core.hook.event`. Each carries `agentId`, `sessionKey`, and
`timestamp` by contract, plus hook-specific fields.

**Required diff (every plugin):**

```diff
- api.on(HookName.BEFORE_AGENT_START, (event, ctx) -> {
-     String agentId = extractString(event, "agentId", "unknown");
-     String channelId = extractString(event, "channelId", null);
-     metrics.recordAgentInvocation(agentId, channelId);
-     return null;
- });
+ api.on(AgentStartedEvent.class, event -> {
+     metrics.recordAgentInvocation(event.agentId(), null);
+     return null;
+ });
```

```diff
- api.on(HookName.BEFORE_PROMPT_BUILD, (event, ctx) -> {
-     if (event instanceof String sp) return sp + EXTRA;
-     return event;
- });
+ api.on(BeforePromptBuildEvent.class, event ->
+     event.withSystemPrompt(event.systemPrompt() + EXTRA));
```

**Removed:**

- `io.jaiclaw.core.hook.HookName` — the enum is gone. Plugins that
  imported it must switch to the event-class API.
- The `Object context` argument from `HookHandler` — context fields
  (agentId, sessionKey, timestamp) now live on the event itself.
- The `extractString` / `extractBoolean` casting helpers the audit
  called out — typed accessors replace them.

**Migrations in-repo (PR-bundled):** `ObservabilityPlugin`,
`TelegramDocStorePlugin`, `TelegramSubscriptionPlugin`,
`ResearchAssistantPlugin`, `CodeScaffolderPlugin`,
`DataPipelinePlugin`, `IncidentResponderPlugin` — every plugin that
shipped with JaiClaw is on the typed API. External plugin authors get
the diff above as their migration template.

**Detection guard.** The new `HookEventTypesSpec` locks the 16-permit
sealed hierarchy and contract fields. A PR that drops or renames a
subtype fails this spec at build time.

---

## P3.4 — Auto-configuration split (LANDED)

**What changed.** The 782-LOC monolithic
`io.jaiclaw.autoconfigure.JaiClawAutoConfiguration` is replaced by seven
domain-scoped auto-configs:

```
JaiClawHttpAutoConfiguration         (HTTP proxy)
JaiClawTenantAutoConfiguration       (TenantProperties, TenantGuard)
JaiClawToolsAutoConfiguration        (ToolRegistry, exec policies, image/voice/video tools)
JaiClawPluginAutoConfiguration       (PluginRegistry, PluginDiscovery)
JaiClawMemoryAutoConfiguration       (memory search, circuit breaker, hook dispatcher)
JaiClawSkillsAutoConfiguration       (SkillLoader)
JaiClawAgentAutoConfiguration        (SessionManager, AgentRuntime, ChannelRegistry, tenant agent factories)
```

The DAG runs Http → Tenant → Tools → Memory → Skills → Plugin → Agent →
Gateway → Channels. `JaiClawAgentAutoConfiguration` is the **last** entry
in the chain — it has the same "after the whole framework" semantics that
the old monolith had at boot time.

**Who's affected.** Adopters whose code does any of:

1. `@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")`
2. `spring.autoconfigure.exclude=io.jaiclaw.autoconfigure.JaiClawAutoConfiguration`
3. `@Import(JaiClawAutoConfiguration.class)` (rare)
4. Imports the class directly anywhere in their codebase

**Required diff:**

```diff
- @AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
+ @AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
```

```diff
- spring.autoconfigure.exclude=io.jaiclaw.autoconfigure.JaiClawAutoConfiguration
+ # Exclude the specific domain auto-config you want to suppress.
+ # See docs/INDEX.md for the full DAG.
+ spring.autoconfigure.exclude=io.jaiclaw.autoconfigure.JaiClawToolsAutoConfiguration,io.jaiclaw.autoconfigure.JaiClawMemoryAutoConfiguration
```

**Bean override migration.** Most adopters override individual beans (e.g., a
custom `SessionManager`); the bean names and types are unchanged so those
overrides keep working. Adopters who use `@AutoConfiguration(before = X)`
or `@AutoConfigureBefore(X)` against the monolith need to pick the
right downstream auto-config — usually `JaiClawAgentAutoConfiguration` if
they need the override to land before any framework-wired agent beans
are created.

**Detection guard.** The new `JaiClawAutoConfigurationOrderSpec` runs in
CI; a future PR that re-introduces the monolith or removes a domain
auto-config from the imports file fails fast there.
