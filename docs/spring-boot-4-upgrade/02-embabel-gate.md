# 02 — The Embabel Gate

> Embabel is JaiClaw's binding Tier-1 dependency (agent runtime via GOAP). It is the **only** dependency without a Boot-4-compatible release, and therefore the single hard gate on cutting 1.0.0 with Spring Boot 4.
> Primary sources: [issue #1052](https://github.com/embabel/embabel-agent/issues/1052) · [Spring Boot 4 / Spring AI 2.0 migration wiki](https://github.com/embabel/embabel-agent/wiki/Spring-Boot-4---Spring-AI-2.0-Migration) · verified 2026-07-13.

## 1. Where Embabel stands (issue #1052 digest)

[#1052 "Spring Boot 4 support"](https://github.com/embabel/embabel-agent/issues/1052) — opened 2025-11-18, **still open**, labels `spring-boot`, `waiting-on-3rd-party`, **`release-2.0`**. Key beats:

- **2025-11-21 (johnsonr, lead maintainer):** attempted the migration at Boot 4.0 GA; blocked because **Spring AI 1.1 is binary-incompatible with Spring Framework 7** (`HttpHeaders.addAll` method incompatibility between spring-ai-openai 1.1.x and spring-web 7.0.x). "This may not be fixed until Spring AI 2.0. So for now this is not feasible."
- **2026-06-12:** Spring AI 2.0.0 GA ships → the stated blocker is gone.
- **2026-07-07 (community):** confirmation that Embabel **0.5.0 still fails on Boot 4** — `NoClassDefFoundError: org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer` (class moved to Boot 4's `spring-boot-micrometer-observation` module).
- **No maintainer ETA** has been posted for a Boot-4 release.

### Migration work in flight (unreleased)

| Ref | What | State |
|---|---|---|
| [PR #1697 "2.0.0"](https://github.com/embabel/embabel-agent/pull/1697) | Full modernization: Boot 4, Jackson `com.fasterxml.jackson.*` → `tools.jackson.*`, version 0.5.0-SNAPSHOT → 2.0.0-SNAPSHOT, `spring-boot-webmvc-test`, explicit `spring-retry` 2.0.12 | Open **draft**, 266 files, unmerged |
| [PR #1765 "1.5.0"](https://github.com/embabel/embabel-agent/pull/1765) | Boot 4 + Spring AI 2.0 compat for `embabel-agent-a2a` + `embabel-agent-anthropic`; `AnthropicModelFactory` rewritten onto the official `anthropic-java` SDK | Open **draft**, 270 files (opened 2026-07-06 — the newer, active line) |
| `2.0.0` branch of embabel-build | Pins **Boot 4.1.0, Spring AI 2.0.0 GA, Kotlin 2.2.21, MCP SDK 2.0.0, anthropic-java 2.40.1, openai-java 4.36.0, Java 21** ([pom](https://raw.githubusercontent.com/embabel/embabel-build/2.0.0/pom.xml)) | Branch only |
| `main` | Still 0.5.0-SNAPSHOT on **Boot 3.5** — Boot-4 work NOT merged | — |

Reading: Boot-4 support will ship as **1.5.0 or 2.0.0** (the `release-2.0` label vs. the newer `1.5.0` branch — unconfirmed which lands first). Their target stack matches our decision D2: **Boot 4.1 + Spring AI 2.0 GA**.

### Released versions (Maven Central)

0.2.0 → 0.3.0–0.3.5 → 0.4.0 → **0.5.0 (2026-06-21, latest)**. 0.5.0 targets **Boot 3.5.14 / Spring AI 1.1.7 / Kotlin 2.1.10 / Jackson 2.21.1** — i.e., exactly JaiClaw's current stack. ([metadata](https://repo.maven.apache.org/maven2/com/embabel/agent/embabel-agent-dependencies/maven-metadata.xml), [0.5.0 pom](https://repo.maven.apache.org/maven2/com/embabel/agent/embabel-agent-api/0.5.0/embabel-agent-api-0.5.0.pom))

## 2. Gate criteria for cutting 1.0.0

1.0.0 (Boot 4) may be cut when **all** of:

- [ ] Embabel publishes a **non-SNAPSHOT** release (Central or repo.embabel.com libs-release) whose BOM targets Spring Boot 4.x + Spring AI 2.0.x.
- [ ] The `AnthropicModelsConfig` startup regression flagged on their migration wiki is confirmed fixed or has a documented workaround we've validated.
- [ ] JaiClaw's Phase 4 checklist ([08-execution-plan.md](08-execution-plan.md)) passes against the GA version — in particular the MiniMax-via-Anthropic-endpoint route and the `camel-html-summarizer-embabel` example.
- [ ] Root pom `embabel-agent.version` points at the GA; no `libs-snapshot` repository needed at build time.

**Monitoring** (check weekly, or wire a scheduled task):
- https://github.com/embabel/embabel-agent/releases (watch for 1.5.0 / 2.0.0)
- https://github.com/embabel/embabel-agent/issues/1052 (close event)
- https://repo.maven.apache.org/maven2/com/embabel/agent/embabel-agent-dependencies/maven-metadata.xml (authoritative)

## 3. Strategy while gated

### 3a. Now, on Boot 3.5 (Phase 0): move 0.3.5 → 0.5.0

Drop-in by stack (same Boot 3.5 / Spring AI 1.1.7). Shrinks the eventual jump and picks up: BYOK + tool-loop callbacks + thinking blocks (0.3.x line), streaming refactor / guardrails / Anthropic caching (0.4.0), OCI starter + native structured output (0.5.0). Regression-check `jaiclaw-embabel-delegate` (AgentPlatform/AgentProcess/ProcessOptions API) and the three-layer model config in CLAUDE.md.

### 3b. On the branch (Phase 4): build against Embabel snapshots

The root pom already declares `embabel-snapshots` (repo.embabel.com libs-snapshot). Pin `embabel-agent.version` to the Boot-4 snapshot (verify whether `1.5.0-SNAPSHOT` or `2.0.0-SNAPSHOT` is actually being published — this could not be confirmed via the Artifactory UI on 2026-07-13; if neither is published, build Embabel's `1.5.0`/`2.0.0` branch locally to the local repo, or from the sibling checkout at `/Users/tap/dev/workspaces/openclaw/embabel-agent`). **Snapshot pins never leave this branch.**

### 3c. Rejected: forcing Embabel 0.x onto Boot 4 for release

A community workaround runs Embabel 0.3.5 on Boot 4 in production (manually define a `Jackson2ObjectMapperBuilder` bean; register `JavaTimeModule` on Embabel's exposed ObjectMapper; exclude the observability module). Rejected for JaiClaw: fragile against Spring AI 1.1-vs-Framework 7 binary incompatibilities, requires excluding observability, and it's exactly the "fork Embabel locally" operational nightmare RELEASE-PLAN-1.0.0.md warned about. Useful only as a diagnostic reference.

## 4. What changes for JaiClaw's Embabel integration (consumer checklist)

Distilled from Embabel's migration wiki, mapped to our repo (27 files reference `com.embabel`; 16 poms):

### Code — `extensions/jaiclaw-embabel-delegate`
- Uses `AgentPlatform`, `AgentProcess`, `AgentProcessStatusCode`, `ProcessOptions`, `@Agent`/`@Action`/`@AchievesGoal`, `OperationContext`, `LlmOptions`, `DefaultModelSelectionCriteria`. The wiki does not flag these core APIs as changed, but they cross a 0.x→1.5/2.0 major boundary — **treat as unverified** and recompile early in Phase 4.
- Jackson boundary: the delegate serializes GOAP goal results with `com.fasterxml.jackson.databind.ObjectMapper`. Embabel 1.5/2.0 is Jackson 3 (`tools.jackson`) — any `ObjectMapper` instance crossing the JaiClaw↔Embabel boundary (incl. Embabel's platform-services ObjectMapper) must be Jackson 3 after Phase 2.

### Config — the three-layer model configuration (CLAUDE.md § Embabel LLM Model Configuration)
- **Layer 1** (`embabel.models.default-llm`) — registered model names may change with the new SDK-based factories; re-verify the registered-name list (`claude-sonnet-4-5` etc.) against the new starter.
- **Layer 2** (`spring.ai.anthropic.*`) — property shape changes with Spring AI 2.0: **`.options` segment removed** (`spring.ai.anthropic.chat.options.model` → `spring.ai.anthropic.chat.model`). More importantly the Anthropic path moves from Spring AI's `RestClient`-based `AnthropicApi` to the **official `anthropic-java` OkHttp SDK**: re-verify `base-url` override behavior for **MiniMax via `api.minimax.io/anthropic`** — this is our documented routing pattern and it now flows through a different HTTP stack. Known issue on their branch: `AnthropicModelsConfig` startup exception under the `anthropic-models` profile.
- **Layer 3** (starter choice) — `embabel-agent-starter-anthropic` remains the right single starter for the MiniMax pattern. OpenAI-side: `completionsPath`/`embeddingsPath` are ignored in Spring AI 2.0 / openai-java (bake paths into `baseUrl`; eager URL validation) — affects any OpenAI-compatible portal profiles (chutes, qwen-portal, minimax-portal in `start.sh login`).
- **`MiniMaxThinkingFilterAutoConfiguration`** wraps ChatModel beans via BeanPostProcessor; Embabel's new Anthropic options add first-class thinking config (`thinkingEnabled(budget)` / `thinkingDisabled()`, `thinkingDisplay` in Spring AI 2.0) — evaluate whether the filter is still needed, and re-test the documented `SmartInitializingSingleton` approach for Embabel-internal ChatModels.
- **spring-retry**: Boot 4 BOM dropped it; Embabel adds 2.0.12 explicitly. If any JaiClaw module uses spring-retry relying on Boot's dependencyManagement, add an explicit version (audit in Phase 1).
- **Observability**: Embabel's observability module on Boot 4 requires `spring-boot-micrometer-observation` and gates on `@ConditionalOnBean(Tracer.class)` — mirror this in gateway-app if we enable it.

### Docs to update in Phase 8
- CLAUDE.md "Version Alignment (Embabel 0.3.5 compatibility)" + the three-layer section (registered model names, `.options` property renames).
- `docs/dev/RELEASE-PLAN-1.0.0.md` "The 2.0 ceiling" — superseded by this plan (decision D1).
