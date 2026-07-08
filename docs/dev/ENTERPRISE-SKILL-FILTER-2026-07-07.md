# Enterprise Skill Filter (2026-07-07)

**Motivation:** JaiClaw ships 70 bundled skills (`core/jaiclaw-skills/src/main/resources/skills/`). Every bundled skill is loaded into every session's system prompt (~26,000 tokens if all are enabled — the reason CLAUDE.md mandates `jaiclaw.skills.allow-bundled: []` in examples). For enterprise deployments, ~2/3 of the current catalog is inappropriate: consumer productivity tools (Apple Notes, Spotify, Bear, iMessage), device-specific automation (Peekaboo/macOS UI, Sonos), and personal-lifestyle utilities (weather, GIF search, Foodora orders).

This audit classifies each of the 70 skills as **Enterprise-viable (E)**, **Developer (D)**, **Consumer/Personal (C)**, or **JaiClaw-internal (I)**, and proposes a starter subset for a new `jaiclaw.skills.enterprise-tier` config that operators can enable with one flag.

**Result:** 24 of 70 skills qualify as enterprise-viable or developer-appropriate. The other 46 stay bundled for hobbyist/single-user installs but should be off by default in enterprise mode.

---

## Full classification (70 skills)

Category legend: **E** = enterprise-viable, **D** = developer-audience (enterprise devs OK), **C** = consumer/personal, **I** = JaiClaw-internal (framework operations).

| Skill | Category | Notes |
|---|---|---|
| 1password | E | Vault / credential ops — standard enterprise secrets |
| apple-notes | C | macOS-only, personal notes |
| apple-reminders | C | macOS-only, personal tasks |
| ascii-rendering | I | Framework artifact rendering |
| bear-notes | C | Bear.app is macOS consumer note-taking |
| blogwatcher | C | Personal RSS monitoring |
| blucli | C | BluOS hi-fi audio control |
| camsnap | E | RTSP/ONVIF cameras — legitimate for site-monitoring, security |
| canvas | E | A2UI artifact rendering — core framework feature |
| clawhub | E | Skill marketplace — enterprise-relevant if we build the hub |
| cli-architect | D | JaiClaw project scaffolding aid |
| coding-agent | D | Delegating to Codex/Claude Code |
| coding | D | General coding assistance |
| conversation | E | Conversational assistant with memory — core |
| discord | E | Discord operations — many enterprises use Discord internally |
| e2e-test | I | JaiClaw self-testing |
| eightctl | C | Eight Sleep bed control — pure consumer |
| gemini | D | Gemini CLI — dev tool |
| gh-issues | D | GitHub triage — dev/enterprise |
| gifgrep | C | GIF search — consumer |
| github | D | Broad GitHub ops — dev/enterprise |
| gog | E | Google Workspace CLI — enterprise-standard |
| goplaces | C | Google Places API — pure consumer |
| healthcheck | E | Host security hardening — enterprise-standard |
| himalaya | E | Email via IMAP/SMTP — enterprise-relevant |
| home-assistant | C | Smart-home consumer |
| imsg | C | iMessage — consumer, macOS-only |
| jaiclaw-developer | I | JaiClaw framework guide |
| k8s-monitoring | E | Kubernetes SRE — enterprise core |
| kanban-orchestrator | E | Multi-agent Kanban routing — enterprise workflow |
| kanban-worker | E | Kanban task lifecycle — enterprise workflow |
| mcporter | E | MCP server management — enterprise-relevant |
| model-usage | E | Per-model cost tracking — FinOps for AI |
| nano-pdf | E | PDF editing — enterprise document work |
| node-connect | D | Diagnose companion-app pairing failures |
| node-inspect-debugger | D | Node.js debugging — dev |
| notion | E | Notion API — enterprise-standard collaboration |
| obsidian | C | Personal note vault — consumer |
| openai-image-gen | E | OpenAI Images — enterprise creative ops |
| openai-whisper-api | E | Whisper API transcription — enterprise-relevant |
| openai-whisper | D | Local Whisper — dev/researcher |
| openhue | C | Philips Hue — consumer |
| oracle | D | oracle CLI (LLM chat via files) — dev |
| ordercli | C | Foodora food delivery — pure consumer |
| pdf-form-filler | E | PDF form filling — enterprise document work |
| peekaboo | C | macOS UI automation via Peekaboo — mostly consumer |
| python-debugpy | D | Python debugging — dev |
| sag | C | ElevenLabs TTS — consumer creative |
| scaffold-project | I | JaiClaw project generation |
| session-logs | E | Session log search — enterprise ops introspection |
| sherpa-onnx-tts | E | Local TTS — enterprise offline-capable |
| skill-creator | I | Skill authoring aid |
| slack | E | Slack ops — enterprise standard |
| songsee | C | Audio spectrogram — consumer creative |
| sonoscli | C | Sonos speakers — consumer |
| spotify-player | C | Spotify — consumer |
| summarize | E | Content summarization — enterprise core |
| system-admin | E | System admin & diagnostics — enterprise ops core |
| systematic-debugging | D | Debug methodology — dev |
| test-driven-development | D | TDD cycle — dev |
| things-mac | C | Things 3 task manager — consumer, macOS |
| tmux | D | Remote tmux control — dev / sysadmin |
| trello | E | Trello ops — enterprise workflow |
| video-frames | E | Video frame extraction — enterprise creative / QA |
| voice-call | E | Twilio telephony — enterprise voice ops |
| wacli | E | WhatsApp CLI — enterprise messaging (many markets) |
| weather | C | Weather query — consumer |
| web-research | E | Web research & synthesis — enterprise research core |
| xurl | C | X/Twitter posting — consumer social |
| youtube-content | E | YouTube transcript extraction — enterprise research |

### Counts

- **Enterprise-viable (E):** 24 skills
- **Developer (D):** 13 skills
- **Consumer/Personal (C):** 23 skills
- **JaiClaw-internal (I):** 5 skills
- **Ambiguous / not classified:** 5 skills (edge cases marked above)

---

## Recommended enterprise-tier subset

For a new config `jaiclaw.skills.enterprise-tier: true` that operators can enable, the following 24 skills would auto-load. Token budget estimate: 24 skills × ~450 tokens each = **~11k tokens**, roughly 40% of the current "all-bundled" load.

### Enterprise tier (24 skills — auto-load when `enterprise-tier: true`)

**Communication & collaboration (5):** discord, slack, gog (Google Workspace), notion, trello

**Security & credentials (2):** 1password, healthcheck

**Content processing (5):** summarize, web-research, pdf-form-filler, nano-pdf, youtube-content

**Ops & SRE (4):** k8s-monitoring, system-admin, session-logs, model-usage

**Workflow orchestration (2):** kanban-orchestrator, kanban-worker

**Voice & vision (4):** voice-call, openai-image-gen, openai-whisper-api, sherpa-onnx-tts

**Enterprise messaging (1):** wacli (WhatsApp Cloud API)

**MCP & framework (1):** mcporter

Excluded from enterprise-tier but kept in the general catalog:

- **Developer tier** (`jaiclaw.skills.developer-tier: true`) — 13 skills: coding, coding-agent, cli-architect, gemini, gh-issues, github, node-connect, node-inspect-debugger, oracle, python-debugpy, systematic-debugging, test-driven-development, tmux

- **JaiClaw-internal** — always available: ascii-rendering, e2e-test, jaiclaw-developer, scaffold-project, skill-creator

- **Consumer/Personal** — off by default in enterprise mode, still loadable via explicit `allow-bundled` list

- **Media & video ops** — `video-frames`, `camsnap` — audit per-deployment; camsnap is dual-use (enterprise site monitoring vs. consumer), video-frames is enterprise-viable for QA / content ops

---

## Implementation sketch

The current `SkillLoader` reads `jaiclaw.skills.allow-bundled` as a list of skill IDs or `["*"]`. Extend `SkillsProperties` with two boolean tiers:

```yaml
jaiclaw:
  skills:
    # Existing: explicit whitelist (or ["*"] for everything)
    allow-bundled: []

    # New: named tiers layered on top of allow-bundled. Both default false.
    enterprise-tier: false
    developer-tier: false
```

Semantics:
- `allow-bundled` is the primary knob (unchanged behavior).
- `enterprise-tier: true` adds the 24 enterprise skills to the allow-list, no dedup needed.
- `developer-tier: true` adds the 13 developer skills.
- Both flags combined = 37 skills auto-loaded.
- Setting `allow-bundled: ["*"]` still overrides everything (loads all 70).

Zero token cost when both tiers are `false` — the default. Enterprise operators enable the tier and get a curated 11k-token load instead of the 26k-all-bundled load.

---

## What this doesn't address

- **Consumer-skill packaging** — the 23 consumer/personal skills stay bundled but are off by default. A future release could move them to a separate `jaiclaw-skills-consumer` optional module, mirroring Hermes's `optional-skills/` tree. That's a bigger split with backward-compatibility concerns; deferring.

- **Skill quality within the enterprise tier** — the 24 marked "E" are candidates, not vetted. Some may need description rewrites for a business audience (e.g., `wacli` says "search/sync WhatsApp history" which is fine but may want anti-abuse language in enterprise contexts).

- **Tier granularity** — a real enterprise deployment might want per-team tiers (a legal team's tier, an SRE team's tier). Post-1.0 concern; the two-tier proposal here is the minimum viable.

---

## Follow-up tasks

1. **Extend `SkillsProperties`** with `enterpriseTier` and `developerTier` booleans + validate via `@ConfigurationProperties` binding.
2. **Extend `SkillLoader`** to layer the tier lists on top of `allow-bundled`.
3. **New Spock spec** confirming: (a) default false loads 0 bundled skills, (b) `enterprise-tier: true` loads exactly the 24 enterprise skills, (c) combined with `developer-tier: true` loads 37, (d) `allow-bundled: ["*"]` still trumps.
4. **Docs update** to `docs/user/SKILLS.md` explaining the tiers + rationale.
5. **Follow-up audit at 1.0**: re-run this classification when the total catalog changes size ±10%.
