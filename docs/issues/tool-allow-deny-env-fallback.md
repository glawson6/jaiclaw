> **Status:** Fixed in 0.9.1 (see `releases/release-0.9.1.md`).
> `JaiClawAgentAutoConfiguration` now reads `tools.allow` and
> `tools.deny` from `Environment` (indexed-property walk) alongside
> `tools.profile` in the record-binding fallback. YAML that declared
> allow/deny lists is now honored end-to-end. This document is kept as
> historical context — the root-cause record-binding behaviour is still
> unfixed (issue doc option (b)), but the env-fallback patch closes the
> functional + security gap.

# Per-agent `tools.allow` / `tools.deny` are silently dropped

**Module:** `jaiclaw-spring-boot-starter`, `jaiclaw-config`
**Severity:** functional + security gap (allow-list filter doesn't apply, leaving every tool exposed to the chat agent)
**Affects:** any downstream app that uses
`jaiclaw.agent.agents.<id>.tools.allow` or `.deny` to restrict the chat
agent's tool surface area. Discovered while wiring a public Telegram bot
(`jaiclaw-event-agent`) where the chat agent should only see 8 event-*
tools but ends up with all 18 framework + app tools, including
`shell_exec`, `claude_cli`, `file_read`, `file_write`.

## Summary

`JaiClawAgentAutoConfiguration` resolves the tool policy with this code
path (lines ~225–235 of
`jaiclaw-spring-boot-starter/.../JaiClawAgentAutoConfiguration.java`):

```java
io.jaiclaw.config.AgentProperties.ToolPolicyConfig toolPolicy = agentConfig.tools();
String toolPolicyPrefix = "jaiclaw.agent.agents." + properties.agent().defaultAgent() + ".tools";
String envProfile = env.getProperty(toolPolicyPrefix + ".profile");
if (envProfile != null && !envProfile.equals(toolPolicy.profile())) {
    toolPolicy = new io.jaiclaw.config.AgentProperties.ToolPolicyConfig(
            envProfile, toolPolicy.allow(), toolPolicy.deny());
    log.info("Tool policy resolved from Environment (record binding fallback) — profile: {}", envProfile);
}
log.info("Tool policy — profile: {}, allow: {}, deny: {}",
        toolPolicy.profile(), toolPolicy.allow(), toolPolicy.deny());
```

…and again in `resolveToolsFromEnvironment` (lines ~352–378):

```java
String envProfile = env.getProperty(prefix + ".profile");
if (envProfile == null) {
    return null;
}
// ...
io.jaiclaw.config.AgentProperties.ToolPolicyConfig config =
        new io.jaiclaw.config.AgentProperties.ToolPolicyConfig(
                envProfile, List.of(), List.of());   // ← allow & deny hard-coded empty
```

Both paths only read `tools.profile` from the environment. **`tools.allow`
and `tools.deny` are never read.** When Spring's record-binding ends up
on the fallback path (which it does for the agent map in non-trivial
configs), any `allow:` / `deny:` declared in `application.yml` is
silently discarded.

The fallback fires more often than expected: it kicks in whenever the
bound `tools.profile` doesn't match the env-read `tools.profile`. In
practice this means binding partially succeeded — e.g. our `llm.provider`
and `llm.primary` bound correctly under `agents.chat.llm.*`, but the
`tools:` block bound to `ToolPolicyConfig.DEFAULT` (`coding`/`[]`/`[]`)
because something about the record-binder didn't like our shape. The
fallback then *overwrites* the profile from env (good) but **wipes the
already-defaulted allow/deny to `[]`** (bad).

## Reproduction

`application.yml`:

```yaml
jaiclaw:
  agent:
    default-agent: chat
    agents:
      chat:
        id: chat
        name: Event Chat Agent
        llm:
          provider: anthropic
          primary: ${EVENT_AGENT_CHAT_MODEL:claude-sonnet-4-5}
        tools:
          profile: full
          allow:
            - extract_event
            - create_event
            - confirm_event
            - find_event
            - delete_event
            - search_events
            - render_events
            - export_event
        tool-loop:
          mode: explicit
          max-iterations: 10
        system-prompt:
          strategy: classpath
          source: prompts/chat-system.md
          append: true
```

Startup log:

```
INFO  JaiClawAgentAutoConfiguration - Tools config resolved from Environment (record binding fallback) — profile: full
INFO  JaiClawAgentAutoConfiguration - Tool policy resolved from Environment (record binding fallback) — profile: full
INFO  JaiClawAgentAutoConfiguration - Tool policy — profile: full, allow: [], deny: []
```

Per-request log (the agent gets the full unfiltered set):

```
INFO  TenantAgentRuntimeFactory - Tenant 'default' tool resolution:
  profile=FULL, registry size=18,
  resolved 18 tools: [confirm_event, ascii_box, render_events,
    create_event, search_events, claude_cli, file_read, web_search,
    find_event, shell_exec, extract_event, pdf_read_fields, file_write,
    export_event, web_fetch, delete_event, pdf_fill_form, ascii_render]
```

`shell_exec` and `claude_cli` are reachable by a public Telegram bot.
That's the security concern; the model has already been observed
attempting to spawn `claude -p` subprocesses on its own initiative when
it can't figure out a task.

## Why the user-side workaround is fragile

`jaiclaw-event-agent` carries a temporary workaround at
`event-agent-app/src/main/java/net/taptech/eventagent/app/config/ToolPolicyOverride.java`:
inject `JaiClawProperties`, reflectively rewrite the `tools` record
component on the `chat` `AgentConfig` instance from a `@PostConstruct`.
This **does not work** in practice because `properties.agent().agents()`
returns an empty map at `@PostConstruct` time — Spring has bound *some*
parts of `JaiClawProperties` (e.g. `llm.provider`) but the `agents` map
itself is empty in the bean we receive. The framework appears to be
constructing a default `AgentConfig` later via `fromDefaults(...)` rather
than reading our bound map. The reflection target literally does not
exist on the singleton we hold.

## Possible fixes

### (a) Read `allow` / `deny` from the environment in the fallback (recommended, minimal)

Update `resolveToolsFromEnvironment` and the inline fallback in
`agentRuntime(...)` to read all three properties:

```java
String prefix = "jaiclaw.agent.agents." + agentId + ".tools";
String envProfile = env.getProperty(prefix + ".profile");
List<String> envAllow = readStringList(env, prefix + ".allow");
List<String> envDeny  = readStringList(env, prefix + ".deny");
return new ToolPolicyConfig(
        envProfile != null ? envProfile : current.profile(),
        envAllow.isEmpty() ? current.allow() : envAllow,
        envDeny.isEmpty()  ? current.deny()  : envDeny);
```

`readStringList(env, prefix)` needs to walk `prefix[0]`, `prefix[1]`, …
since Spring exposes YAML lists as indexed properties in the
`Environment`. Five-line helper.

Pros: fixes the bug exactly where it is; downstream YAML works as written.
Cons: still papers over the underlying record-binding failure.

### (b) Fix the record binding for `Map<String, AgentConfig>`

Investigate why Spring's `@ConfigurationProperties` record binding
produces an empty `agents` map (or a map whose `AgentConfig` values fall
back to `ToolPolicyConfig.DEFAULT`) given the YAML above. Suspicion:
`AgentConfig` has 15 record components including several deprecated
ones, and one of them throws during construction when the YAML omits it.
A compact constructor that initializes all-nullable components to safe
defaults would let the binder succeed in the common partial-config case.

Pros: addresses the root cause — config-as-written becomes the source of
truth.
Cons: bigger refactor; risk of breaking other consumers that relied on
the current "fallback to DEFAULT" behavior.

### (c) Expose a programmatic override SPI

Add a `TenantToolPolicyOverride` interface that the framework consults
when assembling the policy for a tenant:

```java
public interface TenantToolPolicyOverride {
    Optional<ToolPolicyConfig> override(String tenantId, ToolPolicyConfig current);
}
```

Downstream apps register a bean and return their desired allow/deny list.
Framework calls every registered override and folds the results in
order.

Pros: gives apps a stable, supported hook for tenant-aware policy without
fighting the binder. Mirrors the `AttachmentRouter` SPI shape.
Cons: most apps don't need a programmatic SPI — they just want their
YAML to be honored. Worth doing **alongside** (a) or (b), not instead.

## Recommended fix

(a) immediately — it's a six-line change with no shape impact and would
have made `event-agent`'s YAML work as written. Then schedule (b) for a
follow-up; the record-binder partial-success behavior is going to bite
again elsewhere. (c) only if a second consumer (taptech-sentinel?) asks
for programmatic per-tenant tool policy in a future release.

## Workarounds available today

None that work cleanly. We tried:

1. **`@PostConstruct` mutation** of `JaiClawProperties.agent().agents()`
   via reflection — fails because the bound `agents` map is empty.
2. **`BeanPostProcessor` on `JaiClawProperties`** — fires before binding
   completes, same empty-map failure.
3. **Per-tenant YAML override file** under
   `TenantAgentConfigService#configLocations` — only consulted in MULTI
   tenant mode; our app runs SINGLE.
4. **Custom `TenantAgentConfig` bean** — the framework's
   `TenantAgentConfigService` constructs its own from defaults and does
   not consult a context bean of that type.

The least-bad option for now is **prompt-engineering** the chat agent
(via the classpath system prompt) to never call dangerous tools, paired
with **awareness that the registry surface is wider than intended**.
That's what `jaiclaw-event-agent` is doing while waiting for this fix.

## Related code

- `jaiclaw-spring-boot-starter/src/main/java/io/jaiclaw/autoconfigure/JaiClawAgentAutoConfiguration.java` lines ~115–235 (the fallback chain) and ~352–378 (`resolveToolsFromEnvironment`).
- `core/jaiclaw-config/src/main/java/io/jaiclaw/config/AgentProperties.java` — record definitions including `ToolPolicyConfig` (lines 105–113) and `AgentConfig` (lines 13–93, 15 components).
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/ToolRegistry.java` — `resolveForPolicy(profile, allow, deny)` correctly applies the lists; the bug is purely upstream in *how* the lists reach this method.
- `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/tenant/TenantAgentRuntimeFactory.java` — the per-request consumer that logs the resolved tool list.

## Reproduction smoke test once fixed

After the framework change ships, the deploy at `events.taptech.net`
should log:

```
INFO  TenantAgentRuntimeFactory - Tenant 'default' tool resolution:
  profile=FULL, allow=[extract_event,...,export_event],
  resolved 8 tools: [extract_event, create_event, ...]
```

…not the current 18. Removing
`event-agent-app/.../config/ToolPolicyOverride.java` after the upgrade
should leave the same observed behavior.
