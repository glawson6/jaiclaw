# Contributing to JaiClaw

Thank you for your interest! JaiClaw is an open Java framework for
building AI assistants — channels, tools, skills, multi-tenancy, MCP,
and pipelines all live in one cleanly-layered codebase.

This guide covers the practical things you need to know before opening
a PR.

## Quick start

```bash
# Prerequisites: JDK 21 (we use SDKMAN-installed Oracle 21.0.9 in CI)
export JAVA_HOME=/path/to/jdk-21

# First build — downloads dependencies, installs to local Maven repo
./mvnw install -DskipTests

# Run tests for a single module
./mvnw test -pl :jaiclaw-tools -o

# Run gateway locally (no Docker)
./start.sh local

# Run interactive shell
./start.sh shell
```

The Nexus repo at `tooling.taptech.net` is configured in Maven
settings; if it's unreachable, append `-o` (offline) to any command
after the first dependency download.

## Where to put things

| Type of change | Location |
|---|---|
| New channel adapter | `channels/jaiclaw-channel-<name>/` |
| New built-in tool | `core/jaiclaw-tools/src/main/java/.../builtin/` |
| New skill | `core/jaiclaw-skills/src/main/resources/skills/<name>/SKILL.md` |
| New optional capability | `extensions/jaiclaw-<feature>/` |
| New starter | `jaiclaw-starters/jaiclaw-starter-<name>/` |
| New example | `jaiclaw-examples/<name>/` (see "Example requirements" below) |
| New CLI tool | `tools/jaiclaw-<name>/` |

If your change is reusable across multiple places, it belongs in the
appropriate core or extension module — **not** in an example app.

## House rules

These come up often enough in review that listing them here saves time:

- **Tests use Spock** (Groovy). Specs end in `Spec` and live under
  `src/test/groovy/`. Surefire is configured to discover them.
- **Prefer `@Configuration` over `@Component`.** Compose beans via
  explicit `@Configuration` classes with `@Bean` factory methods.
  See `CLAUDE.md` for the full rationale.
- **Use explicit types, not `var`.** Local variables get their
  concrete type spelled out so PR review doesn't need an IDE.
- **No `Co-Authored-By: Claude` in commit messages.** Write commit
  messages as if you authored them directly.
- **`jaiclaw-core` stays Spring-free.** Pure Java records, sealed
  interfaces, enums. Anything Spring-dependent goes in an
  extension module.

## Multi-tenancy conformance check

Every PR is checked against the multi-tenancy invariant. If your code:

1. Reads or writes any persistence layer (Redis, files, H2, vector
   store, in-memory maps) — keys must be tenant-scoped via
   `TenantGuard.resolveStorageKey(...)` when `TenantGuard.isMultiTenant()`.
2. Spawns async work (`CompletableFuture`, virtual threads,
   `Executor`) — wrap with `TenantContextPropagator.wrap(...)`.
3. Adds a new `ConcurrentHashMap<String, ...>` field holding business
   data — either route the key through `TenantGuard`, annotate the
   field `@TenantAgnostic(reason="...")`, or expand
   `TenantIsolationGuardSpec.TENANT_AWARE_CLASSES`. The spec fails
   the build if any field escapes the policy.

Run the regression guard locally:

```bash
./mvnw test -pl :jaiclaw-core -Dtest=TenantIsolationGuardSpec -o
```

Full multi-tenancy rules: `CLAUDE.md` § "Multi-Tenancy Conformance
Check".

## Example requirements

Every example app must include:

1. A `README.md` with five sections — **Problem**, **Solution**,
   **Architecture**, **Design**, **Build & Run**. See
   `jaiclaw-examples/camel-html-summarizer/README.md` for a reference.
2. `jaiclaw.skills.allow-bundled: []` (or a tight whitelist) in
   `application.yml`. The default `["*"]` adds ~26K tokens of
   irrelevant context per LLM call.
3. The `jaiclaw-maven-plugin` in `<build><plugins>` with the
   `analyze` goal bound to the `verify` phase.

The plugin enforces the skills rule and surfaces token-cost issues at
build time.

## Pull request workflow

1. Fork & branch from `main`.
2. Make your change. Add or update Spock specs.
3. Run the affected module(s):
   ```bash
   ./mvnw test -pl :affected-module -am -o
   ```
4. If you touched build/deploy infrastructure, also run:
   ```bash
   ./mvnw install -DskipTests          # full reactor compiles
   ```
5. Open a PR using the template. Fill out:
   - **What changed and why**
   - **Test plan** (commands you ran)
   - **Multi-tenancy check** (yes/no/N-A per the rules above)

## Reporting security issues

Please **do not** open a public GitHub issue. See [SECURITY.md](SECURITY.md)
for the responsible-disclosure process.

## Documentation

If your change adds, moves, or renames a module, class, or
configuration property, update the affected documentation in the same
PR:

- `CLAUDE.md` — directory layout, dependency graph, architecture notes
- `docs/INDEX.md` and the appropriate guide under `docs/user/` or
  `docs/dev/`
- `docs/dev/ARCHITECTURE.md` for system-level changes
- `docs/user/OPERATIONS.md` for runtime/deployment behavior changes
- The relevant `releases/release-X.Y.Z.md` if the change is shipping
  in a tagged release

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md).
Participation in this project is contingent on respecting it.

## Questions?

Open a [GitHub Discussion](https://github.com/glawson6/jaiclaw/discussions)
or email **gregory.lawson@taptech.net**.
