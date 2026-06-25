# Plan: `install.sh --from-source` — install JaiClaw by building from a git checkout

**Target:** 0.9.4 (deferred from 0.9.3, where the immediate gap was instead closed by publishing snapshots to Nexus).

**Status:** Design discussion. Not yet committed.

---

## Why this exists

Today `install.sh` has two installation paths:

1. **Local-build copy** — when the script is run from a cloned repo with a freshly-built jar, copy `apps/jaiclaw-cli/target/jaiclaw-cli-*-exec.jar` into `$JAICLAW_HOME/bin/`.
2. **Nexus download** — otherwise, resolve the canonical jar URL via the Nexus `maven-metadata.xml` (release or snapshot) and `curl` it.

Both paths assume the artifact already exists *somewhere*. Neither covers:

- "Install a specific git branch or PR head on a fresh box that doesn't have the source."
- "Install commit `abc123` from main that hasn't been deployed yet."
- "Install offline-ish from a known git URL when the operator can't access the corporate Nexus."

The right escape hatch is: clone the repo, detect or install the toolchain (Java + git), build, install the resulting jar. That's `--from-source`.

## Trade-off vs. snapshot publishing

| Concern | Nexus snapshot | `--from-source` |
|---|---|---|
| Install time on a cold box | ~30 sec (jar download) | 5–15 min (first Maven build) / ~60 sec subsequent |
| Reproducible to a specific commit | Only via timestamped snapshot ID | Any branch / tag / SHA |
| Requires Nexus access | Yes | No (just GitHub + Maven Central deps) |
| Network bandwidth | ~80 MB | ~500 MB+ (Maven deps cold cache) |
| Disk usage on the install target | ~80 MB | ~2 GB during build, ~500 MB resident (`~/.m2` + bin) |
| Maintenance burden in `install.sh` | None | ~100 new lines, more failure modes |

For routine iteration: snapshot publishing wins. For installing a branch or testing a PR head: `--from-source` wins. Both will coexist.

## Invocation surface

```bash
# Default: install from the latest main HEAD
curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --from-source

# Specific ref (branch, tag, or SHA)
JAICLAW_REF=feat/some-branch curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --from-source

# Local clone (re-use an existing checkout)
JAICLAW_SOURCE_DIR=$HOME/dev/jaiclaw curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --from-source
```

The third form is the most operator-friendly for repeated branch testing: if you're already iterating, point at your checkout and skip the clone.

## Flow

```
1. Parse --from-source / JAICLAW_REF / JAICLAW_SOURCE_DIR
2. Check Platform                    [reuses detect_platform]
3. Check git                         [NEW: detect_git]
   ├─ Present       → continue
   └─ Missing       → bail with clear message; do NOT try to install git
                       (xcode-select on macOS is interactive GUI, apt
                        needs sudo, both are surprising under curl|bash)
4. Check Java                        [reuses probe_java + install_java_via_sdkman]
5. Acquire source                    [NEW: acquire_source]
   ├─ JAICLAW_SOURCE_DIR set         → use as-is, no clone
   └─ Otherwise                       → git clone --depth 1 --branch $REF
                                        into $JAICLAW_HOME/src
6. Build                             [NEW: build_from_source]
   ├─ ./mvnw package -pl :jaiclaw-cli -am -DskipTests
   ├─ Time-honest logging (see below)
   └─ Cache hit on subsequent runs   → ~60 sec instead of cold-cache 5-15 min
7. Install the built jar             [reuse install_jar's local-build path]
8. setup_path                        [unchanged]
```

## "Time-honest" logging

The biggest UX risk is a silent multi-minute Maven build with no feedback. The new logs:

```
▸ Detecting toolchain
✓ git 2.45.0 found
✓ Java 21 found (/Users/op/.sdkman/candidates/java/current/bin/java)

▸ Cloning glawson6/jaiclaw@main into /Users/op/.jaiclaw/src (≈10 seconds)
✓ Cloned (78 MB, 2.3s)

▸ Building jaiclaw-cli from source
   First-time build: downloading ~500 MB of Maven dependencies.
   This typically takes 5–15 minutes on a cold ~/.m2 cache.
   Subsequent builds reuse cached deps and finish in under 60 seconds.
   Progress is printed to install.log; tail it in another terminal:
     tail -f $JAICLAW_HOME/install.log

[ live Maven output streams to install.log; install.sh shows a single
  spinner / heartbeat line every 30 seconds: "Building... (3m12s elapsed)" ]

✓ Build complete (jaiclaw-cli-0.9.3-SNAPSHOT-exec.jar, 78 MB, 8m23s)
```

Two implementation notes:

- **Use a log file, not raw stdout** — Maven is verbose, color-coded, and progress-line-heavy. Streaming it directly to the operator's terminal during `curl | bash` ruins the install.sh aesthetic. Pipe Maven to `$JAICLAW_HOME/install.log` and surface a heartbeat from a background loop.
- **Background heartbeat** — `( while kill -0 $maven_pid 2>/dev/null; do sleep 30; print "Building... (${elapsed}s)"; done ) &`. Kill the loop on completion.

## Detection logic

Reusing the new helpers from 0.9.3:

- `probe_java` (quiet detector) — already added
- `install_java_via_sdkman` — already added; works the same for source-builds
- New `detect_git()` — `command -v git || return 1`. Print canonical version if found.
- New `acquire_source()` — handles `JAICLAW_SOURCE_DIR` short-circuit plus `git clone --depth 1`.
- New `build_from_source()` — wraps `./mvnw package -pl :jaiclaw-cli -am -DskipTests` with the heartbeat + log-file plumbing.

## Cache strategy

Maven's `~/.m2/repository` is the cache. Default behavior:

- **First install:** cold cache, 500+ MB downloaded, 5–15 min. Honest in the logs.
- **Re-install from source on the same box:** warm cache, ~60 sec.
- **`--clean-cache` flag:** force `rm -rf ~/.m2/repository/io/jaiclaw` (delete just JaiClaw artifacts, leave third-party deps). Useful when re-testing a SNAPSHOT after a code change.
- **`--keep-source` flag:** preserve `$JAICLAW_HOME/src` after the build. Default: delete it to save 500+ MB (Maven cache remains).

## Disk usage heads-up

The install starts with a clear notice:

```
▸ This installation will use approximately:
   - 500 MB during build (Maven dependency download)
   -  80 MB resident (the jaiclaw-cli jar)
   - 500 MB resident in ~/.m2/repository (Maven dep cache; reused by future builds)

   Cancel now with Ctrl-C if disk space is a concern.
```

Then a 5-second wait before continuing — gives the operator a chance to bail but doesn't require interaction in non-interactive mode.

## Failure modes

| Failure | Detection | Response |
|---|---|---|
| git missing | `command -v git` | Bail with explicit install instructions per platform |
| git clone fails | exit code | Bail; do NOT auto-retry (likely auth or network) |
| Java install fails | reuse 0.9.3 path | Bail with manual-install fallback |
| Maven build fails | `./mvnw` exit code | Surface the last 50 lines of `install.log`; bail |
| Built jar missing | `find target/jaiclaw-cli-*-exec.jar` | "Build succeeded but no jar found" — bug in this script; bail |
| Disk full during build | Maven's own error message | Bail; mention the 500 MB requirement |

No silent fallbacks. If `--from-source` is requested, success means a built jar; anything else aborts.

## Open questions for discussion

1. **Should `--from-source` be the default fallback when Nexus 404s?** I.e. if `JAICLAW_VERSION=0.9.4-RC1` doesn't resolve on Nexus, automatically try git clone instead. Pro: graceful UX. Con: silent escalation from "fast download" to "5-15 min build" is surprising.
2. **Should there be a `--build-from-pr <number>`** that fetches a GitHub PR head ref? Useful for testing PRs without remembering the branch name. Adds GitHub API call complexity to install.sh.
3. **What about Docker as an even faster path?** "Install JaiClaw" → `docker pull io.jaiclaw/jaiclaw-cli:0.9.3-SNAPSHOT` instead of building. Skips Java install entirely. Different concern: needs the Docker images to be published per snapshot too, which we haven't been doing.
4. **Should we surface the snapshot timestamp in the prompt** (via `${version}` placeholder) so operators running the prompt feature on a from-source install can tell at a glance which build is loaded? Minor, but resonates with the 0.9.3 prompt work.

## What this plan deliberately doesn't do

- Doesn't replace Nexus snapshots — both coexist.
- Doesn't ship with system-Maven detection — `./mvnw` is the canonical entry, system Maven is not assumed.
- Doesn't try to install git on platforms where doing so is interactive or requires sudo. Bail with a message; that's clearer.
- Doesn't introduce a separate `jaiclaw-build` script — the entry point stays `install.sh` so the curl-installable user experience is unchanged.

## Estimated scope

| Item | LOC delta | Notes |
|---|---|---|
| `install.sh` from-source helpers | +100 | Three new functions + flag parsing |
| `install.sh` time-honest logging | +30 | Heartbeat + log file plumbing |
| Sync to `jaiclaw.io/public/install.sh` | byte-copy | As always |
| Documentation in `docs/user/CLI-REFERENCE.md` | +40 lines | New "Install from source" section |
| `docs/user/OPERATIONS.md` Disk-usage note | +10 lines | One paragraph |

Total: ~180 lines added to install.sh + ~50 lines of docs. No Java code changes. No test changes (install.sh has no test harness yet — possibly worth bats coverage in a later release).
