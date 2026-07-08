# Sibling Survey: OpenClaw + Hermes Agent (2026-07-07)

Snapshot of the OpenClaw (TypeScript) and Hermes Agent (Python) projects, framed against JaiClaw's current state. Purpose: identify what's worth porting, what's genuinely novel, and what's out-of-scope so we can decide priorities without re-inventorying each time we look.

Both sibling repos were `git pull`'d immediately before this survey (2026-07-07).

---

## Part 1 — Executive summary

**One-line take:**

- **OpenClaw** continues to widen its lead in surface area (skills, channels, providers) while its architecture evolves toward per-agent memory isolation on the `agent-memory-unification` branch. Nothing existentially threatening to JaiClaw's enterprise-Java positioning, but a lot of concrete capabilities we could port.
- **Hermes Agent** is a Nous Research Python rewrite that's spiritually adjacent — same "SOUL.md / MEMORY.md / skill-based" DNA as OpenClaw, but reimplemented top to bottom with three novel patterns worth attention: **automation blueprints**, **lazy provider installation**, and **skills-as-data with auto-generated catalog docs**.

**Top 5 things to consider adopting (in rough priority order):**

1. **Automation blueprints** (Hermes) — parameterized scheduling templates that unify dashboard forms, CLI slash-commands, agent seed prompts, and docs cards from a single source. High leverage, moderate scope.
2. **Auto-generated skill / blueprint catalogs in docs** (both) — a small Python or Node script extracts a manifest from source-of-truth files and writes JSON that a docs component renders. Would upgrade our static markdown docs.
3. **Per-agent memory scoping** (OpenClaw `agent-memory-unification` branch, ~6.4k commits ahead) — global baseline + per-agent overrides, cross-agent QMD/session search opt-in, agent-scoped dreaming/artifacts. This is the closest sibling analog to what an enterprise multi-tenant deployment would need.
4. **Anthropic classifier safety pattern** (Hermes) — the "godmode moved to optional because it poisons every session's system prompt" lesson. We should audit every bundled skill's description for similar risk before 1.0.
5. **MCP stdio watchdog + process reaping** (Hermes) — production-hardening for MCP servers (idle timeout recycling, orphan reaping, SIGTERM forwarding). Worth mirroring in our MCP tool provider layer.

**Not worth porting (yet):**

- Native macOS/iOS/Android apps + device nodes (OpenClaw) — enterprise-Java positioning doesn't overlap
- Full skill parity (OpenClaw has 58 bundled, Hermes 205; we have 6) — the *ratio* is misleading because both siblings ship consumer/personal skills we don't want in an enterprise agent framework
- Godmode / red-teaming skills (both) — same reason, and the classifier lesson argues against including them in a bundled catalog at all
- Ink → OpenTUI transition (Hermes) — we're a Spring Shell REPL, not a TUI app
- Python-specific hardening (`uv.lock`, lazy_deps.py) — different toolchain

---

## Part 2 — Counts by category

Baseline: what `docs/dev/FEATURE-COMPARISON.md` claimed at last review. "Now" is per current-main state of each repo.

| Category | JaiClaw (main) | OpenClaw (main) | Hermes (main) | Notes |
|---|---|---|---|---|
| **Bundled skills** | 6 (per FEATURE-COMPARISON; core catalog has grown, needs re-count) | 58 (was 50+) | 86 bundled + 119 optional | See skill catalog below |
| **Channels** | 11 | 23 bundled | 9–12 active | OpenClaw adds IRC, Twitch, Nostr, Mattermost, Nextcloud Talk, Synology Chat, Tlon, Zalo, QQ Bot, Feishu |
| **LLM providers** | 11 | 35+ | 16 native | We're extendable via Spring AI starters |
| **Built-in tools** | 38 | 8 categories × varied | 82+ core | Different categorization, hard to compare 1:1 |
| **Native apps** | 0 | macOS + iOS + Android | 0 (but TUI) | Out of scope for enterprise-Java positioning |
| **MCP tool providers** | 22 in-repo | Not counted | Not counted | JaiClaw is arguably ahead here |
| **Automation blueprints** | 0 | 0 | ~30 | Novel Hermes concept |

**Verdict:** Sibling surface-area advantage is real but concentrated in consumer/personal-productivity skills we don't ship by design. The gaps that actually matter for our enterprise-framework positioning are narrower: memory scoping (OpenClaw branch), automation blueprints (Hermes), classifier safety (Hermes), and process-lifecycle hardening for MCP (Hermes).

---

## Part 3 — OpenClaw findings

### Full bundled-skill catalog (58 total)

Skills that already have a JaiClaw analog are marked (JC).

`1password`, `apple-notes`, `apple-reminders`, `bear-notes`, `blogwatcher`, `blucli`, `camsnap`, `canvas` (JC), `clawhub`, `coding-agent` (JC), `diagram-maker` **NEW**, `discord` (JC), `eightctl`, `gemini` (JC), `gh-issues`, `gifgrep`, `github` (JC), `gog`, `goplaces`, `healthcheck` (JC), `himalaya`, `imsg`, `mcporter`, `meme-maker` **NEW**, `model-usage`, `nano-pdf`, `node-connect`, `node-inspect-debugger` (JC), `notion`, `obsidian`, `openai-whisper`, `openai-whisper-api` (JC), `openhue`, `oracle`, `ordercli`, `peekaboo`, `python-debugpy` (JC), `sag`, `session-logs` (JC), `sherpa-onnx-tts` (JC), `skill-creator` **NEW** (JC has this), `slack`, `songsee`, `sonoscli`, `spike`, `spotify-player`, `summarize` (JC), `taskflow` **NEW**, `taskflow-inbox-triage` **NEW**, `things-mac`, `tmux`, `trello`, `video-frames` **NEW**, `voice-call` (JC), `wacli`, `weather`, `xurl`.

**New since baseline:** `diagram-maker`, `meme-maker`, `taskflow`, `taskflow-inbox-triage`, `video-frames`, `skill-creator` (they got one; we already had it), `claw-score` (internal, not in the public skill folder).

### Bundled-channel catalog (23 total)

Adapter tree: `clickclack`, `discord`, `googlechat` (JC), `imessage`, `irc`, `signal` (JC), `slack` (JC), `telegram` (JC), `twitch`, `whatsapp` (JC), `webhooks`, plus bundled plugins: `feishu`, `line` (JC), `matrix` (JC), `mattermost`, `msteams` (JC), `nextcloud-talk`, `nostr`, `qqbot`, `synology-chat`, `tlon`, `zalouser` / `zalo` (2 variants), `qa-channel` (test).

**Channels JaiClaw doesn't have:** IRC, Twitch, Nostr, Mattermost, Nextcloud Talk, Synology Chat, Tlon, Zalo, QQ Bot, Feishu, iMessage, plus the "clickclack" internal channel.

### `agent-memory-unification` branch

**Status:** ~6,412 commits ahead of main. Not yet merged. Heavy churn = active feature development, not abandoned.

**What it introduces:**
- Per-agent memory configuration (global baseline + per-agent overrides)
- Scoped dreaming / QMD artifacts by agent (not shared across agents)
- Shared vault bridge for opt-in cross-agent access
- Agent-specific memory-maintenance targeting (prune, repair, migrate)
- Cross-agent `memorySearch.qmd.extraCollections` — an agent can search another agent's session transcripts if permitted
- Multi-agent testing fixture alignment

**Why it matters for JaiClaw:** Our multi-tenant story rests on `TenantContext` + `TenantGuard` + per-tenant persistence prefixes. That's fine for data-isolation but doesn't address the harder problem of **per-agent memory scoping within a tenant** (e.g., "an ops agent and a support agent both belong to Tenant A but should have separate dreaming logs"). OpenClaw's branch is solving exactly that.

**Recommendation:** Read the branch's commit log + a couple of the config-schema files ourselves before deciding whether to adopt the pattern. If we do, it slots naturally into `jaiclaw-agentmind-*` extensions rather than requiring core changes.

### Other new OpenClaw capabilities (post-baseline)

- **Computer-use plugin** — macOS node input executor + computer agent tool. Early-stage. Not a threat/opportunity for us; we don't do desktop agent control.
- **Sessions full-text search** (`sessions_search` tool) — searchable session-transcript history. Cheap to port if we want it; probably valuable for our `TranscriptStore` SPI.
- **Event triggers via cron** — polled condition watchers as first-class cron feature. Complements what our pipeline module already does; not obviously better.
- **Snapshot database targets** — memory-DB snapshots with artifact versioning + rollback. Operational, not API-shape.
- **Standing orders** — persistent agent instructions in markdown. Lightweight pattern; overlaps with our `SoulProvider` overlay work.
- **Lobster integration** — workflow pipeline orchestration inside OpenClaw. Not enough detail to evaluate; noted for follow-up.

---

## Part 4 — Hermes Agent findings

### What it is

- **Python 3.11+** application, current release `0.18.0`, by Nous Research.
- Positioned explicitly as **"the self-improving AI agent"** and as a **successor to OpenClaw** (Hermes can migrate OpenClaw data).
- Runs on a `$5 VPS`, Docker, SSH, Singularity, Modal serverless, GPU clusters, or your local machine. Design premise: one process serves every messaging platform simultaneously.
- Full-terminal TUI (dual-engine: Ink + newer OpenTUI in Rust).
- Not a fork — a rewrite. Shares conceptual DNA (SOUL.md, MEMORY.md, skill-based automation) with OpenClaw but reimplements the entire stack.

### Full bundled-skill catalog (86 in `/skills`)

Grouped by category. Skills also present in JaiClaw are marked (JC).

- **Apple (4):** apple-notes, apple-reminders, findmy, imessage
- **Autonomous AI Agents (4):** claude-code, codex, hermes-agent, opencode
- **Computer Use (1):** computer-use-mcp
- **Creative (16):** architecture-diagram, ascii-art, ascii-video, baoyu-infographic, claude-design, comfyui, design-md, excalidraw, humanizer, manim-video, p5js, popular-web-designs, pretext, sketch, songwriting-and-ai-music, touchdesigner-mcp
- **Data Science (1):** jupyter-live-kernel
- **Dogfood (2):** references, templates
- **Email (1):** himalaya
- **GitHub (6):** codebase-inspection, github-auth, github-code-review, github-issues, github-pr-workflow, github-repo-management
- **Media (4):** gif-search, heartmula, songsee, youtube-content
- **MLOps (4):** evaluation, huggingface-hub, inference, models
- **Note-Taking (1):** obsidian
- **Productivity (8):** airtable, google-workspace, maps, nano-pdf, notion, ocr-and-documents, petdex, powerpoint, teams-meeting-pipeline
- **Research (6):** arxiv, blogwatcher, llm-wiki, polymarket, research-paper-writing
- **Smart Home (1):** openhue
- **Social Media (1):** xurl
- **Software Development (9):** hermes-agent-skill-authoring, node-inspect-debugger (JC), plan, python-debugpy (JC), requesting-code-review, simplify-code, spike, systematic-debugging (JC), test-driven-development (JC)
- **Yuanbao (1):** yuanbao-skill

Plus **119 optional** skills in `/optional-skills`, including relocated `godmode` (red-teaming) and `obliteratus` (open-weight model refusal removal).

### The automation-blueprints concept

**One-sentence framing:** A parameterized scheduling template, defined once as a Python dataclass, that renders itself into four surfaces: dashboard form, CLI slash-command, docs card, and agent seed prompt.

**Source of truth:** `cron/blueprint_catalog.py` — Python dataclass with `schedule_template`, `prompt_template`, and typed `BlueprintSlot` definitions.

**Docs pipeline:**
1. `website/scripts/extract-automation-blueprints.py` imports the catalog and emits `/website/static/api/automation-blueprints-index.json`.
2. The React component `AutomationBlueprintsCatalog/index.tsx` fetches that JSON and renders a card grid.
3. `website/scripts/prebuild.mjs` runs the extractor before `npm start` / `npm run build`.

**Card contents:** Title, human-readable schedule ("Daily at 9 AM"), description, tags, pre-filled `/blueprint` slash-command with copy button, deep-link to the desktop app.

**~30 shipped blueprints** in six categories: Development, DevOps, Research, GitHub events, Payments/Business, multi-skill pipelines.

**Design philosophy quotes worth borrowing:**
- "Users never type raw cron."
- The `[SILENT]` return pattern: if a cron-driven agent finds nothing noteworthy, it emits `[SILENT]` and delivery is suppressed.

**Contrast with our world:** Our `jaiclaw-pipeline` module has YAML pipeline definitions + triggers (MANUAL/HTTP/FILE/CRON/CAMEL_URI). That's a pipeline framework; Hermes blueprints are a **usability layer on top of one**. The interesting thing to port isn't the cron machinery — we already have that — it's the "one dataclass, four renderers" pattern.

### The godmode relocation lesson

**What happened:** In commit `fdc90346eaa3931fb357543b9224515728cac914` (2026-06-09), Nous moved two bundled skills (`red-teaming/godmode`, `mlops/inference/obliteratus`) to `optional-skills/`.

**Why:** Anthropic's output classifier on Claude models (particularly `claude-fable-5`) was returning **empty content** for sessions whose bundled-skills catalog was injected into the system prompt with those descriptions. A/B testing showed presence of those lines → **95% block rate**; absence → **25% block rate**. The skills weren't even being loaded; just their catalog descriptions were tripping the classifier.

**Fix:** Move them to `optional-skills/` so the bundled catalog stays clean. Users who need them do `hermes skills install <name>`.

**JaiClaw implication:** We inject bundled-skill catalogs into system prompts by design (`jaiclaw.skills.allow-bundled`). We should audit every bundled skill's description text for anything that could plausibly trip a safety classifier, and pre-emptively move risky ones to a workspace-skills tier rather than shipping them in the always-loaded catalog. Concrete action: run one bundled-skill catalog through Claude and see if any sessions get blocked with our current 6-skill core.

### Other Hermes patterns worth noting

- **Skills-guard + AST audit + threat-patterns** (`tools/skills_guard.py`, `tools/skills_ast_audit.py`, `tools/threat_patterns.py`) — automated skill analysis for CVE detection, threat pattern matching, source verification. We have a skill approval flow; we don't have automated pre-approval auditing.
- **MCP stdio watchdog + orphan reaping** — idle timeout recycling of MCP servers, SIGTERM forwarding, cross-platform kill guards. Directly applicable to our `McpToolProvider` runtime.
- **Lazy dependency installation** — provider SDKs installed on first use, not bundled. Different from Maven's model, but the spirit ("only pay for what you use") could inform how we structure `jaiclaw-starters/`.
- **Exact-pinned dependencies + uv.lock** — every dep pinned to `==X.Y.Z`, no ranges. Motivated by a real supply-chain incident (Mini Shai-Hulud worm hitting `mistralai 2.4.6` on 2026-05-12). Maven equivalent is a lockfile via the `versions:lock-snapshots` or a hand-managed BOM discipline; worth checking whether we're currently exposed.
- **i18n as YAML data files** — locales indexed at runtime from `locales/*.yaml`. We have `JaiClawMessages` + `ResourceBundle`; not obviously better either way.
- **Skills marketplace / hub** — the `optional-skills/` tree points at an `agentskills.io` marketplace concept. OpenClaw's `clawhub` is the parallel. Long-term hint that skills-as-a-marketplace is a viable pattern.

---

## Part 5 — Discussion points

Not decisions — questions worth debating before we prioritize:

1. **Is enterprise-Java positioning still the right frame if OpenClaw is doing enterprise-grade per-agent memory scoping on a branch?** Or does that just mean we adopt the pattern faster?

2. **Automation blueprints could ship as an extension** (`jaiclaw-blueprints` sitting on top of `jaiclaw-pipeline`). The React catalog component would need a home — we don't have a website today. Is that the moment to build one, or do we render as static markdown for now?

3. **The classifier-safety audit is a 1.0 blocker if we agree with the concern.** It's cheap (an hour of Claude sessions with `allow-bundled: ["*"]` and observation of block rate), but it needs someone to actually run it.

4. **MCP process-lifecycle hardening is table-stakes for a production framework** and we're behind. Worth its own small implementation task.

5. **How much of the OpenClaw skill catalog do we even want?** The counts look scary (58 vs 6) but most are consumer productivity (Spotify, Bear Notes, Apple Reminders). If we filter to "skills an enterprise operator would enable," the gap shrinks to maybe 5–10 skills — and we'd want to author them for our SPI, not port.

6. **The `sessions_search` tool** is a small, high-value item that composes well with our `TranscriptStore` SPI. Worth doing without further discussion?

---

## Appendix — Investigation pointers

Things flagged during the survey that we didn't drill into:

- OpenClaw's `agent-memory-unification` branch — read a few config-schema commits before making an adoption decision.
- OpenClaw's `docs/automation/taskflow.md` — durable task orchestration model. Compare against our `ExplicitToolLoop` and Embabel plan.
- Hermes's `cron/blueprint_catalog.py` — full read of the Python file to understand slot types and defaults.
- Hermes's `AutomationBlueprintsCatalog/index.tsx` — read the React component + its CSS to understand the card UI.
- Hermes's `tools/threat_patterns.py` — see what the threat-pattern detection actually looks for.
- OpenClaw's `computer-use` plugin manifest — one-file read to understand the arming/capability model.
