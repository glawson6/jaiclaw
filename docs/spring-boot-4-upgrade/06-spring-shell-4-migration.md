# 06 — Spring Shell 3.4 → 4.0 Migration

> Spring Shell 4.0.x is the Boot-4 line (current: 4.0.2, built against Boot 4.0.6 — verify a 4.1-compatible patch at execution time). It is a **major rewrite**, not a bump. Prerequisite per the migration guide: be on 3.4.x first — we are (3.4.2).
> Source: [v4 migration guide](https://github.com/spring-projects/spring-shell/wiki/v4-migration-guide) · [releases](https://github.com/spring-projects/spring-shell/releases)

## 1. What changes

| Spring Shell 3.x | Spring Shell 4.x |
|---|---|
| `@ShellComponent` on class | **removed** — commands auto-scanned under Boot; class-level grouping via `@CommandGroup` |
| `@ShellMethod(key = "...")` | **`@Command`** (methods only) in `org.springframework.shell.core.command.annotation` |
| `@ShellOption` | **`@Option`** / **`@Argument`** |
| `@ShellMethodAvailability` | availability API reworked — map per command during rewrite |
| `@CommandScan` | removed (auto-scan) |
| packages `org.springframework.shell.standard.*` | `org.springframework.shell.core.*` |
| modules `spring-shell-standard`, `spring-shell-standard-commands` | consolidated into core |
| table API `org.springframework.shell.table` | moved to `spring-shell-jline`, package `org.springframework.jline.tui.table` |
| built-in `stacktrace`, `completion` commands | removed |
| interactive/non-interactive mixed at runtime | **chosen at startup** |

## 2. Repo surface (~35 files, every one rewritten)

| Module | Files w/ shell annotations | Notes |
|---|---|---|
| `apps/jaiclaw-shell-commands` | **25** | The shared command library (ChatCommands, OnboardWizard, StatusCommands…) — the bulk of the work |
| `apps/jaiclaw-cli` | 4 | Profile-aware standalone CLI; `bin/jaiclaw` fast-path (no JVM) unaffected; JVM-path commands rewritten |
| `apps/jaiclaw-cron-manager-app` | 1 | |
| `tools/*` (perplexity, rest-cli-architect ×3, skill-creator, prompt-analyzer, project-scaffolder) | 7 | Dual-mode library/standalone pattern — retest `-Pstandalone` fat jars |
| `jaiclaw-examples/*` (aiops, competitive-intel, contract-reviewer, gemma4-local, invoice-processor, sales-enrichment, support-triage, travel-planner) | 8 | |

## 3. JaiClaw-specific risks

1. **The hyphenated-alias convention.** CLAUDE.md documents `@ShellMethod(key = {"pplx search", "pplx-search"})` because 3.x `NonInteractiveShellRunner` joins CLI args with spaces. Shell 4's runner model changed (interactive vs non-interactive chosen at startup) — **retest multi-word vs hyphenated command keys in non-interactive mode first**, then either keep the dual-alias convention or simplify. This affects `start.sh`, `bin/jaiclaw`, e2e scripts, and every README showing CLI invocations.
2. **Startup-time mode selection** may change how `jaiclaw-shell` (interactive REPL) vs `jaiclaw-cli` (one-shot) bootstrap — check `spring.shell.*` properties and `NonInteractiveShellRunner` replacement config in both apps' ymls.
3. **Availability + help text**: `--help` blocks are a 1.0 documentation-parity gate (RELEASE-PLAN); verify generated help output shape after rewrite.
4. **Table rendering**: StatusCommands / auth-status colored tables — if they use `org.springframework.shell.table`, imports move to `org.springframework.jline.tui.table` + add `spring-shell-jline` dependency.
5. **`jaiclaw-starter-shell`** starter pom + `spring-shell-dependencies` BOM coordinates (`spring-shell.version` 3.4.2 → 4.0.x) — confirm the BOM artifact name is unchanged in 4.x.
6. Embabel's Boot-4 build pins `spring-shell-starter 3.4.0` on their branch — if `embabel-agent-shell` is on our classpath anywhere (it shouldn't be — verify), watch for a Shell 3/4 clash.

## 4. Suggested execution (Phase 5)

1. Bump `spring-shell.version` → 4.0.x; fix `apps/jaiclaw-shell-commands` first (25 files) — it's the shared library the rest depend on.
2. Mechanical annotation mapping per file: `@ShellComponent` → remove (add `@CommandGroup` where the class key-prefix mattered), `@ShellMethod(key=...)` → `@Command(command=...)`, `@ShellOption` → `@Option`.
3. Rebuild `jaiclaw-shell`, `jaiclaw-cli`, `cron-manager-app`; then tools; then examples.
4. Retest the documented invocation matrix:

```bash
# interactive REPL
./start.sh shell                      # then: pplx search query-style keys
# non-interactive (the risky path)
java -jar tools/jaiclaw-perplexity/target/*.jar pplx-search "query" --num-results 5
bin/jaiclaw chat "hello"
bin/jaiclaw --profile work chat
./start.sh auth json
```

5. Update CLAUDE.md "Spring Shell CLI Module Pattern" section with the 4.x rules that replace the 3.x lore (Phase 8).
