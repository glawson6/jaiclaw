# JaiClaw 1.3.0 — Implementation Plan

*Written 2026-09-09 against `main` @ `dbeecf31`. Assumes 1.2.0 has shipped per
[`IMPLEMENTATION-PLAN-1.2.0.md`](./IMPLEMENTATION-PLAN-1.2.0.md) — in
particular `IterationBudget`, `SubAgentLauncher`, `ToolSearch`, ESTOP and
`jaiclaw-learning`. Scope source:
[`feature-gap-analysis-2026-09-09.md`](../../feature-gap-analysis-2026-09-09.md) Part 6.*

**Status:** not started — blocked on 1.2.0 release. Current phase: **Phase 1 (§6)** once unblocked.

---

## 1. Context

> **See also:** [`DESIGN-1.3.0-SKILL-EXTRACTION.md`](./DESIGN-1.3.0-SKILL-EXTRACTION.md)
> — a proposed redesign of the 1.2.0 learning loop as batch extraction over the
> `jaiclaw-audit` transcript corpus. **Not scheduled work.** It documents a
> prerequisite (an outcome signal) that should land before any of it is built,
> and records that `jaiclaw-audit` becomes a *strict* dependency of skill
> extraction rather than the optional one it is in 1.2.0.

1.3.0 is the **protocols + safety** release. 1.2.0 made JaiClaw agents learn
and delegate; 1.3.0 makes them interoperable with other agents (A2A),
recoverable when they damage a workspace (checkpoints + rollback), cheaper
per turn (prompt-cache discipline, code-execution sandbox), and rounds out
the session primitives both competitors ship (`/goal`, `/loop`, `/heartbeat`,
session search, credential pools, `clarify`, context-file injection).

### Decisions locked in before Phase 1

- **A2A ships as a channel adapter + an outbound tool**, not as a new
  runtime. Inbound: `jaiclaw-channel-a2a` implements the A2A v1.0 server
  surface (Agent Card, `tasks/send`, `tasks/get`, streaming via SSE) on top of
  `AbstractChannelAdapter`. Outbound: `a2a_call` tool in the same module.
  Use the official `a2a-java` SDK if its 1.0 artifact is on Central at start
  of phase; otherwise implement the JSON-RPC surface directly (it is small)
  and record the decision. ACP (editor protocol) stays deferred.
- **Checkpoints use a single shared shadow git store** (`~/.jaiclaw/checkpoints/store`,
  JGit), never the workspace's own `.git` — Hermes' design. Opt-in per agent.
- **Code execution is an SPI with GraalJS as the default impl** and a
  Docker impl behind `SafeProcessEnvironment`. The tool is `execute_code`;
  scripts call tools through an in-process RPC bridge (`tools.call(name, args)`),
  never through the shell.
- **Prompt-cache discipline is an invariant, then a feature.** First make the
  system prompt byte-stable for the life of a session (spec-enforced), then
  emit provider cache markers through Spring AI 2.0 chat options where the
  provider supports them (Anthropic `cache_control`, Bedrock Nova, OpenAI
  prompt cache key).
- **Session primitives are session state**, stored in `Session` attributes so
  Redis-backed sessions carry them, and rendered into a *stable* prompt block
  (goal text changes rarely; loops/heartbeats never touch the prompt).
- **Estimates are solo-developer weeks with Claude Code.**

### Explicitly out of scope

- ACP adapter for editors (Zed / VS Code) — adopter-driven.
- Computer-use tool, meeting bots, long-tail channels/providers, MoA.
- Vector-backed tool search ranking (lexical from 1.2.0 stays).
- Cloud worker / remote sandbox provisioning (Crabbox/Daytona/Modal class) —
  Docker sandbox only; SSH/K8s runners can be SPI impls later.

---

## 2. How to use this plan (and conventions)

Identical to 1.2.0 §2: Resume-here pointers per phase, `[ ]`/`[x]` tasks,
Definition of Done gates, Spock specs in the same commit, `@Configuration` +
`@Bean`, **no AI attribution in commit messages**, module/BOM/starter/imports
in the same phase, multi-tenancy conformance before closing any phase that
persists state, decision log in §12.

---

## 3. Module layout (new in 1.3.0)

| Module | Type | Purpose |
|---|---|---|
| `channels/jaiclaw-channel-a2a` | channel | A2A v1.0 server (inbound) + `a2a_call` tool (outbound) |
| `jaiclaw-starters/jaiclaw-starter-a2a` | starter | |
| `extensions/jaiclaw-checkpoints` | extension | Shadow-git snapshots + rollback |
| `extensions/jaiclaw-code-exec` | extension | `execute_code` SPI, GraalJS + Docker sandboxes, tool RPC bridge |
| `extensions/jaiclaw-secrets-vault` | extension | HashiCorp Vault `SecretsProvider` (carried from July report) |
| `extensions/jaiclaw-image-generation` | extension | Provider SPI wrapping Spring AI `ImageModel` (mirrors `jaiclaw-video-generation`) |
| `jaiclaw-examples/a2a-peer-agents` | example | Two gateways calling each other over A2A (E2E anchor) |

Modified: `jaiclaw-core` (permits, `PromptCacheBoundary`, `SessionGoal`),
`jaiclaw-agent` (prompt stability, goal/loop/heartbeat, credential pools),
`jaiclaw-tools` (`ClarifyTool`, `SessionSearchTool` promotion), `jaiclaw-audit`
(FTS index), `jaiclaw-config` (`ModelsProperties` fallbacks/pools),
`jaiclaw-code` (checkpoint hooks), `jaiclaw-cron` (loop/heartbeat jobs),
`apps/jaiclaw-shell-commands` (`/goal`, `/loop`, `/heartbeat`, `/rollback`).

---

## 4. Pattern precedents

All 1.2.0 §4 precedents plus:

| Purpose | File |
|---|---|
| Channel adapter with webhook inbound + REST outbound | `channels/jaiclaw-channel-telegram/` (adapter + autoconfig shape) |
| Generic webhook adapter (1.2.0 Phase 5) | `channels/jaiclaw-channel-webhook/` |
| Workspace path containment | `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/exec/{WorkspaceBoundary,SafeProcessEnvironment}.java` |
| File mutation tools to hook for checkpoints | `extensions/jaiclaw-code/src/main/java/io/jaiclaw/code/FileEditTool.java`, `core/jaiclaw-tools/.../builtin/{FileWriteTool,ShellExecTool}.java` |
| Transcript store / search results | `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/{TranscriptStore,FileTranscriptStore,TranscriptSearchResult,mcp/SessionSearchMcpToolProvider}.java` |
| Model/provider config | `core/jaiclaw-config/src/main/java/io/jaiclaw/config/ModelsProperties.java` (`ModelProviderConfig.fallbackModel` exists) |
| Per-tenant chat model cache | `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/tenant/TenantChatModelFactory.java` |
| System prompt assembly | `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/SystemPromptBuilder.java` + `core/jaiclaw-skills/.../SkillPromptBuilder.java` |
| Secrets SPI + reference impl | `extensions/jaiclaw-secrets-1password/` |
| Provider SPI with async jobs (template for image gen) | `extensions/jaiclaw-video-generation/` |
| Existing image tool to migrate | `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/ImageGenerationTool.java` |
| Cron job + executor | `extensions/jaiclaw-cron/src/main/java/io/jaiclaw/cron/{CronService,CronJobExecutor}.java` |

Competitor references (design parity only):

| Concept | OpenClaw | Hermes |
|---|---|---|
| A2A channel | `extensions/a2a/` | `plugins/platforms/a2a/` |
| Checkpoints / rollback | managed worktrees | `tools/checkpoint_manager.py`, `user-guide/checkpoints-and-rollback.md` |
| Code mode / execute_code | `docs/tools/code-mode.md` (QuickJS-WASI) | `tools/code_execution_*.py`, `tools/code_kernel*.py` |
| Prompt cache | `docs/concepts/context-engine.md` | `agent/prompt_cache_*.py`, `AGENTS.md` invariants |
| Goal / loop / heartbeat | `docs/tools/goal.md`, heartbeat | `user-guide/features/{goals,loops,heartbeat}.md` |
| Credential pools / fallback | model failover | `agent/credential_pool.py`, `agent/fallback_cooldown.py` |
| Session search | session-search | `hermes_state_fts.py`, `tools/session_search_tool.py` |
| Clarify / ask-user | ask-user cards | `tools/clarify_tool.py` |
| Context files | AGENTS.md bootstrap | `user-guide/features/context-files.md` |

---

## 5. End-to-end testing

Story, grown per phase:

> Two JaiClaw gateways (`planner`, `researcher`) run in one JVM test. `planner`
> discovers `researcher`'s Agent Card and delegates a task over A2A; the
> researcher edits files in a workspace under checkpoint protection, runs an
> `execute_code` script that calls three tools in one turn, and returns. The
> planner's session carries a `/goal`; a `/heartbeat` re-enters it while idle;
> a bad edit is undone with `/rollback`; the whole run sends a byte-identical
> system prompt on every turn (cache invariant) and survives a simulated 429
> on the primary credential by rotating to the pool's second key.

| Phase | Spec | Asserts |
|---|---|---|
| 1 | `A2AE2ESpec` (channel-a2a) | Agent Card served; `tasks/send` creates session; streaming events; `a2a_call` round-trip; auth rejected without token |
| 2 | `CheckpointsE2ESpec` (checkpoints) | snapshot before `file_edit`; `/rollback` restores bytes; workspace `.git` untouched; per-tenant store isolation |
| 3 | `CodeExecE2ESpec` (code-exec) | script calls 3 tools; resource limits (time/memory) enforced; filesystem/network denied in GraalJS; Docker impl same contract |
| 4 | `PromptStabilityE2ESpec` (agent) | 10 turns, system prompt bytes identical; skill applied mid-session not visible; cache option present in provider request when enabled |
| 5 | `SessionPrimitivesE2ESpec` (agent/tools/cron) | goal persists across restart (Redis session); heartbeat fires only when idle; loop cancels; credential rotation on 429; `clarify` round-trip; `AGENTS.md` injected once |

External: extend `.claude/skills/e2e-test/` with an `a2a` scenario (two
`jaiclaw-gateway-app` containers via `docker-compose/`).

---

## 6. Phase 1 — A2A (`jaiclaw-channel-a2a`)

**Resume here →** first task: SDK decision. | last touched: —

**Estimate:** 2 weeks.

### 6.1 Scope

Inbound: serve an A2A v1.0 Agent Card at `/.well-known/agent.json` (per
agent id, tenant-aware), accept `tasks/send` / `tasks/sendSubscribe` /
`tasks/get` / `tasks/cancel`, map each task to a JaiClaw session
(`{agentId}:a2a:{peerId}:{taskId}`), stream `TaskStatusUpdate` /
`TaskArtifactUpdate` over SSE from `AgentRuntime.runStreaming`. Outbound: an
`a2a_call` tool (and `A2aClient` bean) that resolves a peer card, sends a
task, optionally waits, and returns artifacts as a structured tool result;
`SubAgentLauncher` gains an `A2aSubAgentLauncher` impl so `delegate_task` can
target a remote agent by card URL.

### 6.2 Definition of Done

- `A2AE2ESpec` passes with two in-JVM gateways.
- Agent Card fields populated from `AgentIdentity` + skills (`SkillDefinition` → A2A `skills[]`).
- Peer auth: bearer token via `jaiclaw-security` (api-key/jwt modes); `none` mode logs a warning like the existing SEV-001 pattern.
- Task state machine (`submitted → working → completed | failed | canceled`) persisted with the session; `tasks/get` after restart works with Redis sessions.
- ESTOP refuses new `tasks/send` with 503.
- `a2a_call` respects `IterationBudget`/depth from delegation config.

### 6.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `channels/pom.xml`, root, BOM, `jaiclaw-starters/jaiclaw-starter-a2a/pom.xml` | modify/create | module wiring |
| `channels/jaiclaw-channel-a2a/pom.xml` | create | dep on `a2a-java` (if chosen) or Jackson only |
| `.../a2a/model/{AgentCard,AgentSkill,Task,TaskState,Message,Part,Artifact,TaskStatusUpdateEvent,TaskArtifactUpdateEvent}.java` | create | records (skip if SDK) |
| `.../a2a/server/{AgentCardController,A2aJsonRpcController,A2aSseEmitter,A2aTaskStore}.java` | create | HTTP surface; task store in `Session` attributes |
| `.../a2a/A2aChannelAdapter.java` | create | `AbstractChannelAdapter`; inbound task → `ChannelMessage`; outbound `deliver` → artifact update |
| `.../a2a/client/{A2aClient,AgentCardResolver}.java` | create | `RestClient`-based; card cache with TTL |
| `.../a2a/tool/A2aCallTool.java` | create | `a2a_call(agentUrl|agentName, message, wait, timeout)` |
| `.../a2a/delegation/A2aSubAgentLauncher.java` | create | `SubAgentLauncher` impl selected when request `target` is a card URL |
| `.../a2a/{A2aProperties,A2aChannelAutoConfiguration}.java` | create | `enabled`, `basePath`, `peers[]` (name → card URL + token ref), `cardTtl` |
| `jaiclaw-spring-boot-starter/.../JaiClawChannelAutoConfiguration.java` | modify | register on classpath |
| `jaiclaw-examples/a2a-peer-agents/` | create | two-app compose example |
| `channels/jaiclaw-channel-a2a/src/test/groovy/.../e2e/A2AE2ESpec.groovy` | create | |
| `docs/user/A2A-CHANNEL.md`, `docs/user/MULTI-AGENT.md` | create | |
| `CLAUDE.md` | modify | channel count |

### 6.4 Task list

- [ ] **SDK decision:** check Central for `io.a2a:a2a-java-sdk` 1.0.x; record in §12; scaffold accordingly
- [ ] Module skeleton + starter + autoconfig + properties
- [ ] Agent Card: build from `AgentIdentity` + skills + capabilities (`streaming: true`, `pushNotifications: false`); tenant-aware when `TenantContext` present; serve at `/.well-known/agent.json` and `/{basePath}/agents/{agentId}/card`
- [ ] JSON-RPC controller: `tasks/send`, `tasks/sendSubscribe` (SSE), `tasks/get`, `tasks/cancel`; error codes per spec
- [ ] `A2aTaskStore` in session attributes; state machine; cancel → `AgentRuntime.cancel(sessionKey)`
- [ ] Adapter: task message parts (text/file/data) → `ChannelMessage` + attachments; reply → `Artifact` + status `completed`
- [ ] Auth integration + ESTOP check
- [ ] `A2aClient` + `AgentCardResolver` (TTL cache, ETag if provided)
- [ ] `A2aCallTool` + `A2aSubAgentLauncher` (depth/budget honored)
- [ ] Spock: card contents; state transitions; SSE frames; auth reject; ESTOP 503; client parse
- [ ] Example `a2a-peer-agents` + `A2AE2ESpec`
- [ ] Docs + `CLAUDE.md`; `e2e-test` `a2a` scenario

### 6.5 Verification

`./mvnw test -pl :jaiclaw-channel-a2a -am -o`; `docker compose up` the example
and `curl` the card, then send a task from `planner` via the shell.

### 6.6 Risk & rollback

Spec drift in A2A 1.0 — pin the spec revision in the doc and keep the model
records isolated so an SDK swap is local. Off by default.

---

## 7. Phase 2 — Checkpoints & rollback (`jaiclaw-checkpoints`)

**Resume here →** first task: `CheckpointStore` over JGit. | last touched: —

**Estimate:** 1–2 weeks.

### 7.1 Scope

Before a destructive tool call (`file_edit`, `file_write`, `shell_exec`
matching a destructive pattern, `execute_code` in Phase 3) snapshot the
workspace into a shared shadow repository keyed by workspace path + tenant;
expose `/rollback [n|checkpointId]`, `checkpoint_list`, and an MCP/REST
surface; garbage-collect by count/age.

### 7.2 Definition of Done

- `CheckpointsE2ESpec` passes; workspace's own `.git` is never modified.
- Opt-in: `jaiclaw.checkpoints.enabled=false` default; per-agent override.
- Snapshot cost bounded: `.gitignore`-aware, size cap per file, skip
  binary > N MB, one commit per tool call (debounced within a turn).
- Rollback restores exactly the snapshot bytes; files created after the
  snapshot are removed only when `--clean` is passed.
- Store path is tenant-scoped in multi mode.

### 7.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| module wiring (pom/BOM/starter — fold into `jaiclaw-starter-shell`? decide §12) | | |
| `extensions/jaiclaw-checkpoints/pom.xml` | create | `org.eclipse.jgit` |
| `.../checkpoints/{CheckpointStore,JGitCheckpointStore,Checkpoint,CheckpointProperties,CheckpointsAutoConfiguration}.java` | create | store per (tenant, workspace) as a branch in one shared repo |
| `.../checkpoints/hook/CheckpointBeforeToolHook.java` | create | `ToolCallStartedEvent` listener; destructive-tool predicate (names + shell regex list) |
| `.../checkpoints/tool/{CheckpointListTool,RollbackTool}.java` | create | builtins; `rollback` requires approval floor `PROMPT_ALWAYS` by default |
| `.../checkpoints/mcp/CheckpointMcpToolProvider.java` | create | |
| `.../checkpoints/gc/CheckpointGc.java` | create | count/age policy; cron-driven when available |
| `apps/jaiclaw-shell-commands/.../RollbackCommand.java` | create | `/rollback`, `/checkpoints` |
| `.../e2e/CheckpointsE2ESpec.groovy` | create | |
| `docs/user/CHECKPOINTS.md` | create | |

### 7.4 Task list

- [ ] `JGitCheckpointStore`: init shared bare repo lazily; branch name `cp/{tenant}/{sha1(workspace)}`; `snapshot(workspace, label)` → commit id; `restore(commitId, clean)`; `list(workspace, limit)`
- [ ] Ignore rules: workspace `.gitignore` + built-in (`.git/`, `target/`, `node_modules/`, size cap)
- [ ] `CheckpointBeforeToolHook`: fires on destructive tools; debounce (one snapshot per turn per workspace unless files changed since)
- [ ] Tools + MCP + shell command; rollback approval floor
- [ ] GC (keep last N, drop older than D days), scheduled
- [ ] Spock: snapshot/restore bytes; ignore rules; debounce; tenant isolation; workspace `.git` untouched (hash `.git` dir before/after)
- [ ] `CheckpointsE2ESpec`; docs; `CLAUDE.md`

### 7.5 Verification

`./mvnw test -pl :jaiclaw-checkpoints -am -o`; in the shell, edit a file via
the agent, `/rollback`, `git status` in the workspace shows no shadow noise.

### 7.6 Risk & rollback

Disk growth — GC defaults (50 checkpoints / 14 days) and the size cap. Opt-in;
removing the module leaves an inert `~/.jaiclaw/checkpoints/` directory.

---

## 8. Phase 3 — Code execution sandbox (`jaiclaw-code-exec`)

**Resume here →** first task: `CodeExecutionSandbox` SPI. | last touched: —

**Estimate:** 3 weeks.

### 8.1 Scope

An `execute_code(language, code, timeout)` tool that runs a short script with
access to a `tools` object bridging to the `ToolRegistry` (respecting the
session's active tool set from 1.2.0 Tool Search and the approval floors),
returns stdout + a structured result, and enforces time/memory/IO limits.
Default sandbox: GraalJS (`org.graalvm.polyglot`) with host access disabled
except the bridge. Second impl: Docker container via `SafeProcessEnvironment`
running a tiny JS/Python runner that talks JSON-RPC over stdio back to the
JVM bridge.

### 8.2 Definition of Done

- `CodeExecE2ESpec` passes for GraalJS; Docker impl passes the same contract spec when Docker is available (spec `@Requires` Docker).
- Off by default (`jaiclaw.code-exec.enabled`); tool has approval floor `PROMPT_ALWAYS` by default.
- Scripts cannot touch the filesystem, network or host classes in GraalJS; only `tools.call`, `console.log`, `JSON`, and a bounded `sleep`.
- Every `tools.call` goes through the same approval + policy path as a direct model tool call and is recorded as a `ToolCallStarted/Ended` hook pair.
- Resource limits: wall clock (default 60 s), statement-count/CPU via GraalVM `ResourceLimits`, output cap (64 KB).
- Checkpoint hook (Phase 2) treats `execute_code` as destructive when the script called a destructive tool.

### 8.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| module wiring | | |
| `extensions/jaiclaw-code-exec/pom.xml` | create | `org.graalvm.polyglot:polyglot` + `js` (optional scope for Docker-only users) |
| `.../codeexec/{CodeExecutionSandbox,ExecutionRequest,ExecutionResult,SandboxLimits}.java` | create | SPI |
| `.../codeexec/bridge/{ToolBridge,ToolBridgeInvoker}.java` | create | `call(name, argsJson)` → registry resolve → approval → execute → JSON; enforces active-tool set + floors |
| `.../codeexec/graal/GraalJsSandbox.java` | create | `Context.newBuilder("js").allowHostAccess(NONE).allowIO(false)…`, `ResourceLimits`, bridge injected as a host object with an explicit `@HostAccess.Export` surface |
| `.../codeexec/docker/{DockerSandbox,RunnerProtocol}.java` + `src/main/resources/runner/{runner.js,runner.py}` | create | container with no network (`--network none`), read-only rootfs, tmpfs work dir; JSON-RPC over stdio |
| `.../codeexec/tool/ExecuteCodeTool.java` | create | builtin; language `js` (default) / `python` (Docker only) |
| `.../codeexec/{CodeExecProperties,CodeExecAutoConfiguration}.java` | create | `enabled`, `sandbox: graal|docker`, `limits.*`, `docker.image` |
| `.../e2e/CodeExecE2ESpec.groovy`, `.../SandboxContractSpec.groovy` (abstract, run per impl) | create | |
| `docs/user/EXECUTE-CODE.md` | create | includes the "why this is not a shell" explanation |

### 8.4 Task list

- [ ] SPI + records; `SandboxContractSpec` (abstract Spock base) written **first**
- [ ] `ToolBridge`: active-set check, approval/floor path, hook events, JSON marshalling, per-call timeout
- [ ] `GraalJsSandbox`: builder config, resource limits, output capture, bridge export, error mapping (syntax vs runtime vs limit)
- [ ] `ExecuteCodeTool` + registration + floor default
- [ ] `DockerSandbox` + runners; image build documented (no Dockerfile in-tree — reuse a pinned public image + mounted runner)
- [ ] Checkpoint integration (Phase 2 predicate consults bridge's "destructive call seen" flag)
- [ ] Spock: contract for both impls; escape attempts (Java.type, require, fetch, fs) fail; limits enforced; bridge honors deny floor
- [ ] `CodeExecE2ESpec`; docs; `CLAUDE.md`

### 8.5 Verification

`./mvnw test -pl :jaiclaw-code-exec -am -o` (GraalJS); with Docker running,
`-Djaiclaw.test.docker=true` for the Docker contract.

### 8.6 Risk & rollback

Sandbox escape is the risk; mitigations are `HostAccess.NONE`, explicit
export surface, `allowIO(false)`, no `allowExperimentalOptions`, and the
Docker impl for adopters who want process isolation. Off by default.

---

## 9. Phase 4 — Prompt-cache discipline

**Resume here →** first task: prompt-stability spec. | last touched: —

**Estimate:** 1 week.

### 9.1 Scope

Make the invariant explicit and enforceable: for the life of a session the
system prompt is byte-stable; skill/memory/tool changes apply at the next
session (or on explicit `--now`); compaction is the only sanctioned mutation
of history. Then emit provider cache markers.

### 9.2 Definition of Done

- `PromptStabilityE2ESpec` passes: 10 turns, identical system prompt bytes; `SkillApplied` mid-session not visible; `MemoryUpdated` mid-session not visible (AgentMind injectors already splice at session start — verify, don't assume).
- `PromptCacheBoundary` marks the stable prefix (identity + soul + skills + memory + tool schemas) vs volatile suffix (goal, per-turn context) so volatile content sits *after* the cache point.
- When `jaiclaw.models.prompt-cache.enabled=true`: Anthropic requests carry `cache_control` on the boundary block; Bedrock Nova and OpenAI equivalents set where Spring AI 2.0 options expose them; unsupported providers no-op with a one-time INFO log.
- `TokenUsage` gains `cacheRead`/`cacheWrite` when the provider reports them; Micrometer counter `jaiclaw.prompt.cache.miss` per completed request.
- Docs page states the invariant and lists which operations defer.

### 9.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `core/jaiclaw-core/.../prompt/{PromptCacheBoundary,PromptSection}.java` | create | ordered sections with `stable` flag |
| `core/jaiclaw-agent/.../SystemPromptBuilder.java` | modify | assemble sections; stable first; expose `stableHash()` for specs/diagnostics |
| `core/jaiclaw-agent/.../AgentRuntime.java` (+ collaborator `PromptCacheOptionsApplier`) | modify | apply provider options at the boundary |
| `core/jaiclaw-config/.../ModelsProperties.java` | modify | `promptCache.{enabled, ttl}` per provider |
| `core/jaiclaw-core/.../model/TokenUsage.java` | modify | +cache read/write (defaulted) |
| `extensions/jaiclaw-observability/...` | modify | cache-miss counter |
| `extensions/jaiclaw-learning/...` | modify | ensure apply paths mark "effective next session"; add `--now` to shell `learning apply` that resets the session explicitly (never silent) |
| `core/jaiclaw-agent/src/test/groovy/.../e2e/PromptStabilityE2ESpec.groovy` | create | |
| `docs/dev/PROMPT-CACHE-INVARIANT.md`, `docs/user/PROMPT-CACHING.md` | create | |

### 9.4 Task list

- [ ] Write `PromptStabilityE2ESpec` first (red)
- [ ] `PromptCacheBoundary` + section ordering in `SystemPromptBuilder`; move goal/volatile content after the boundary (goal lands in Phase 5 — leave the slot)
- [ ] Audit every prompt contributor (`SkillPromptBuilder`, AgentMind Soul/Memory/Tendencies injectors, `defaultAdditionalInstructions`, tool schemas) for mid-session mutation; fix or document
- [ ] `PromptCacheOptionsApplier` for Anthropic (Spring AI 2.0 `AnthropicChatOptions` cache control), Bedrock Converse (Nova), OpenAI (`prompt_cache_key`) — feature-detect per provider class
- [ ] `TokenUsage` cache fields + observability counter
- [ ] Learning `--now` semantics
- [ ] Docs; `CLAUDE.md` design-decisions note

### 9.5 Verification

Spec green; against a real Anthropic key, DEBUG-log the request and confirm
`cache_control` on the boundary block and non-zero `cache_read_input_tokens`
on turn 2.

### 9.6 Risk & rollback

Section reordering can change prompt wording order for existing adopters —
call it out in `MIGRATION-1.3.md`; the cache options are off by default.

---

## 10. Phase 5 — Session primitives & resilience

**Resume here →** first task: `SessionGoal`. | last touched: —

**Estimate:** 2–3 weeks (independent sub-tracks; can interleave with Phases 1–4).

### 10.1 Scope

The remaining "small high-leverage" items from the gap analysis, each a
self-contained sub-track:

| Track | Deliverable | Est. |
|---|---|---|
| 5A Goal | `/goal` — one durable objective in `Session` attributes, rendered in the volatile prompt slot, `goal_get/set/clear` tools, shell + MCP surface | 3 d |
| 5B Loop + heartbeat | `/loop <interval> <prompt>` and `/heartbeat <interval> <prompt>` as `jaiclaw-cron` jobs bound to a session; heartbeat fires only when session idle (`isRunning` false); cancel on session close | 4 d |
| 5C Session search | Promote `SessionSearchMcpToolProvider` to a builtin `session_search` tool; add an FTS index behind `TranscriptStore` (H2 `FT_*` when `jaiclaw-cron-manager`'s H2 is present, else in-memory Lucene-free inverted index) with tenant filter | 1 w |
| 5D Credential pools + fallback | `ModelsProperties.ModelProviderConfig.credentials[]` + `fallbackChain[]`; `CredentialPool` rotates on 429/401 with cooldown; `TenantChatModelFactory` evicts on rotation; Micrometer counters | 1 w |
| 5E Clarify tool | `clarify(question, options[], allowFreeText)` builtin → delivered through the originating channel as a structured message (buttons where the adapter supports them — Telegram/Slack/Discord — text fallback); answer resumes the run via the same path `ToolApprovalHandler` uses | 3 d |
| 5F Context-file injection | `SystemPromptBuilder` reads `AGENTS.md` / `CLAUDE.md` / `.jaiclaw.md` from `workspaceDir` at session start (size cap, stable section, injection-pattern scan reusing `PromptRedactor` when compliance present) | 2 d |
| 5G Vault secrets | `jaiclaw-secrets-vault` over `spring-cloud-vault-config` implementing `SecretsProvider` | 1 d |
| 5H Image generation SPI | `jaiclaw-image-generation` mirroring `jaiclaw-video-generation`; migrate `ImageGenerationTool` to the SPI; providers: Spring AI `ImageModel` (OpenAI/Stability), fal (HTTP) | 3 d |

### 10.2 Definition of Done

- `SessionPrimitivesE2ESpec` passes (all tracks).
- Goal/loop/heartbeat survive a Redis-backed session restart; none of them mutate the stable prompt prefix (Phase 4 spec stays green).
- Credential rotation is observable (`jaiclaw.model.credential.rotations` counter) and never logs secret material.
- `clarify` works on at least Telegram + shell; other adapters fall back to text.
- `ImageGenerationTool` behavior unchanged for existing adopters (default provider = today's Spring AI path).

### 10.3 Files to create / modify (per track)

- 5A: `core/jaiclaw-core/.../session/SessionGoal.java`; `core/jaiclaw-tools/.../builtin/{GoalGetTool,GoalSetTool,GoalClearTool}.java`; `apps/jaiclaw-shell-commands/.../GoalCommand.java`; `SystemPromptBuilder` volatile slot.
- 5B: `extensions/jaiclaw-cron/.../session/{SessionLoopJob,SessionHeartbeatJob,SessionJobBinder}.java`; shell `LoopCommand`, `HeartbeatCommand`; cleanup on `SessionEndedEvent`.
- 5C: `extensions/jaiclaw-audit/.../search/{TranscriptIndex,InMemoryTranscriptIndex,H2FtsTranscriptIndex}.java`; `core/jaiclaw-tools/.../builtin/SessionSearchTool.java` (delegates to the audit index via an SPI in core so `jaiclaw-tools` doesn't depend on audit — `TranscriptSearchPort` in `jaiclaw-core`).
- 5D: `ModelsProperties` additions; `core/jaiclaw-agent/.../model/{CredentialPool,CredentialPoolChatModel,FallbackChatModel,CooldownPolicy}.java`; wrapper `ChatModel` decorators applied in `TenantChatModelFactory` and the primary bean.
- 5E: `core/jaiclaw-core/.../agent/{ClarifyRequest,ClarifyHandler}.java` (SPI like `ToolApprovalHandler`); `core/jaiclaw-tools/.../builtin/ClarifyTool.java`; `core/jaiclaw-gateway/.../ClarifyDispatcher.java`; Telegram/Slack/Discord adapters: inline-button rendering + callback mapping.
- 5F: `core/jaiclaw-agent/.../prompt/WorkspaceContextFiles.java`; `AgentProperties.contextFiles.{enabled, names[], maxChars}`.
- 5G: `extensions/jaiclaw-secrets-vault/` (mirror 1Password module).
- 5H: `extensions/jaiclaw-image-generation/` (mirror video-generation); move tool from `jaiclaw-tools` with a deprecation shim for one release.

### 10.4 Task list

**5A Goal**
- [ ] `SessionGoal(text, setAt, setBy)` in session attributes; tools; shell `/goal`; prompt slot after cache boundary
- [ ] Spock: persists via `SessionManager`; prompt prefix unchanged when goal changes

**5B Loop + heartbeat**
- [ ] `SessionJobBinder` creates cron jobs with `sessionKey` metadata; heartbeat checks `AgentRuntime.isRunning(sessionKey)` and skips when busy; loop runs regardless but serializes on the session
- [ ] Cancel on `/loop stop`, `/heartbeat stop`, session close, ESTOP (skip, not delete)
- [ ] Spock: idle gate; cancellation; ESTOP skip

**5C Session search**
- [ ] `TranscriptSearchPort` in core; in-memory index (tokenized, tenant-filtered, top-k with snippets); H2 FTS impl behind `@ConditionalOnClass`
- [ ] `SessionSearchTool` builtin (deferred by default under Tool Search — it's rarely needed)
- [ ] Spock: tenant isolation; ranking; snippet windows

**5D Credential pools + fallback**
- [ ] Config records (`credentials[]`, `fallbackChain[]`, `cooldownSeconds`)
- [ ] `CredentialPoolChatModel` decorator: on 429/401 mark cooldown, rotate, retry once; `FallbackChatModel`: on exhausted pool or 5xx/timeouts, next provider in chain
- [ ] Redaction: never log keys; counters
- [ ] Spock: rotation on 429; cooldown expiry; fallback chain order; `TenantChatModelFactory` eviction

**5E Clarify**
- [ ] SPI + tool + gateway dispatcher (reuse approval-routing path)
- [ ] Telegram inline keyboard; Slack Block Kit buttons; Discord components; text fallback elsewhere
- [ ] Spock: question delivered; answer resumes run; timeout → `ToolResult.Error`

**5F Context files**
- [ ] Reader with size cap and stable-section placement; injection-pattern scan (warn + strip lines matching known patterns; full scan via compliance `PromptRedactor` when present)
- [ ] Spock: injected once; cap; scan strips

**5G Vault**
- [ ] Module + provider + spec against Vault dev server via Testcontainers (`@Requires` Docker)

**5H Image generation**
- [ ] SPI + Spring AI provider + fal provider; migrate tool; deprecation shim
- [ ] Spock: provider selection; tool output unchanged

**Phase close**
- [ ] `SessionPrimitivesE2ESpec`
- [ ] Docs for each track; `CLAUDE.md` counts

### 10.5 Verification

Per-track specs, then the E2E. Manually: shell `/goal`, `/heartbeat 2m "check
the deploy"`, `/loop 30s "tick"`, `/rollback`, and a forced 429 via a bogus
first key in the pool.

### 10.6 Risk & rollback

Every track is additive and flag-gated except 5H's tool move (shim covers one
release) and 5D's decorator on the primary `ChatModel` (only applied when
pools/fallbacks are configured).

---

## 11. Phase 6 — Release

**Estimate:** 3 days.

- [ ] Full `./mvnw clean install` online → `-o`
- [ ] Security scan report per cadence
- [ ] `CHANGELOG.md` 1.3.0; `docs/MIGRATION-1.3.md` (prompt section order; `ImageGenerationTool` move; new `HookEvent` permits)
- [ ] Promote 1.2.0 `@Experimental` SPIs that saw adopter use to `@Stable`; keep 1.3.0 SPIs `@Experimental`
- [ ] `e2e-test` skill: `a2a`, `checkpoints`, `code-exec` scenarios green on staged artifacts
- [ ] Refresh `feature-gap-analysis-*.md` (new dated copy — do not edit the 2026-09-09 baseline in place)
- [ ] Tag `v1.3.0`, deploy, bump `1.4.0-SNAPSHOT`

---

## 12. Decision log

| Date | Decision | Why |
|---|---|---|
| 2026-09-09 | A2A before ACP | Spring adopters interoperate with other agents, not with Zed/VS Code |
| 2026-09-09 | Shadow-git checkpoints, never the workspace `.git` | Hermes design; zero interference with adopter repos |
| 2026-09-09 | GraalJS default sandbox, Docker second | In-JVM, no daemon dependency, strong host-access controls; Docker for process isolation |
| 2026-09-09 | Prompt-cache invariant is spec-enforced before cache markers ship | Markers on an unstable prefix are worse than none |
| 2026-09-09 | Session primitives stored in `Session` attributes | Redis sessions carry them for free; no new stores |
| 2026-09-09 | `TranscriptSearchPort` lives in core | Keeps `jaiclaw-tools` free of an audit dependency |
| 2026-09-10 | Skill extraction, if built, reads the **audit transcript corpus**, not live sessions | A single live session cannot answer "is this durable?" — durability is a claim about recurrence. A corpus can. It also removes the prompt-cache risk by construction and makes extraction evaluable: same corpus in, diff the proposals out. See DESIGN-1.3.0-SKILL-EXTRACTION.md. |
| 2026-09-10 | `jaiclaw-audit` is a **strict** dependency of skill extraction | The 1.2.0 reviewer degrades to `SessionManager` when audit is absent; a batch extractor has no fallback — there is nothing to batch over. Compile-time non-optional, a `TranscriptStore` bean required at runtime, and archiving must have run long enough to build a corpus. Must fail loudly, not silently extract nothing. |
| 2026-09-10 | An **outcome signal** precedes any extraction work | Batch extraction improves how proposals are generated; it does nothing about our inability to tell whether an applied skill helped. Building it first yields a better-engineered guess. |
| — | *(open)* `a2a-java` SDK vs hand-rolled JSON-RPC (Phase 1) | |
| — | *(open)* checkpoints starter placement (Phase 2) | |
