# Competitive Intel Briefing

Scheduled competitive-intelligence briefing built on the JaiClaw pipeline
module. Showcases the **CRON trigger** (powered by Camel quartz) and a
filesystem-cache change-detection pattern.

## Problem

Manual competitive analysis consumes 5–20 hours per week of senior analyst
time at $100–200/hour. AI-driven CI pipelines surface competitor moves within
hours and produce a digest the strategy team actually reads. This is among
the highest-visibility AI deployments — the daily briefing lands directly in
front of leadership.

## Solution

A five-stage pipeline fired by a quartz cron expression
(`0 0 7 ? * MON-FRI` — weekdays at 07:00). The shell command `run-now`
short-circuits the schedule for impatient demos.

1. **collect-signals** (AGENT, with web-search) — gather signals per competitor
2. **detect-changes** (PROCESSOR) — diff vs cached signals from the prior run
3. **synthesize** (AGENT) — "what changed this week"
4. **impact-analysis** (AGENT) — implications + suggested responses
5. **format-briefing** (PROCESSOR) — write `<yyyy-MM-dd>.md` to disk

## Architecture

```
quartz: every weekday 07:00      OR    shell `run-now`
                │                              │
                └─────────────┬────────────────┘
                              ▼
  ┌──────────────────────────────────────────────────────────┐
  │ collect-signals → detect-changes → synthesize            │
  │   (AGENT,full)    (PROCESSOR,disk)  (AGENT)              │
  │ → impact-analysis → format-briefing                       │
  │   (AGENT)            (PROCESSOR, writes md to disk)       │
  └──────────────────────────────────────────────────────────┘
                              │
                              ▼
        ~/.jaiclaw/competitive-intel/briefings/<date>.md
```

## Design

- **CRON via quartz.** The pipeline module's CRON branch now generates a
  `quartz://jaiclaw-pipelines/<id>?cron=<encoded>` URI, so real cron
  expressions are honored. This example depends on `camel-quartz-starter`
  explicitly (the pipeline module ships it as `optional`).
- **Filesystem-backed change detection.** `signalCacheBean` reads the prior
  run's signals from `~/.jaiclaw/competitive-intel/last-run.json`, writes the
  current ones back, and emits a coarse delta the next agent can reason about.
- **Web search built-in.** The agent's `tools.profile: full` exposes
  `WebSearchTool`. Drop to `minimal` if your demo can't reach the network —
  the agent will fabricate plausible signals from prompt context.
- **`@ConfigurationProperties`-driven competitors.** Override the seeded
  `[Acme, Globex, Initech]` list via `jaiclaw.competitive.competitors[]`.

## Prerequisites

- Java 21
- `ANTHROPIC_API_KEY`
- Outbound network (for the web-search tool — turn `profile: minimal` if not)

## Build & Run

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw package -pl :jaiclaw-example-competitive-intel-briefing -am -DskipTests -o
ANTHROPIC_API_KEY=sk-ant-... \
  java -jar jaiclaw-examples/competitive-intel-briefing/target/jaiclaw-example-competitive-intel-briefing-*.jar
```

Inside the shell:

```
shell:> competitors
Acme
Globex
Initech

shell:> run-now
Submitted executionId=… Run `last-briefing` afterwards.

shell:> executions
executionId                           status     ms
…                                     SUCCESS    8420

shell:> last-briefing
--- 2026-06-10.md ---
# Competitive Intel Briefing — 2026-06-10
…
```

## Extension points

- **Real signal sources:** replace the AGENT in `collect-signals` with
  per-source PROCESSOR stages (news API, pricing-page scraper, LinkedIn,
  GitHub release feeds…). The downstream stages don't change.
- **Confluence / Notion publishing:** add an extra PROCESSOR after
  `format-briefing` that POSTs the markdown to your wiki API, or attach a
  CAMEL output via `output().camelUri("http:…")`.
- **Per-tenant customization:** wire a tenant-resolution bean so different
  squads track different competitors via the existing
  `jaiclaw.competitive.competitors` shape.
