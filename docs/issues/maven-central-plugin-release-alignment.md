# Maven Central releases: should we switch to the copilot-sdk's publishing plugin?

**Area:** build / release engineering (root `pom.xml`, `jaiclaw-bom/pom.xml`, `maven-central-deploy/`, `.github/workflows/publish-central.yml`)
**Severity:** process simplification + one latent CI bug found during analysis
**Trigger:** [github/copilot-sdk `java/pom.xml`](https://github.com/github/copilot-sdk/blob/main/java/pom.xml) publishes to Maven Central with a single plugin block and no shell scripts — is that simpler than what we do?

## Problem statement

The copilot-sdk Java module publishes to Maven Central via a plugin declared directly in its POM. JaiClaw currently releases through the gitignored `maven-central-deploy/` script suite (`release.sh`, `dry-run.sh`, `deploy-nexus.sh`, plus five setup scripts). Question: would adopting the copilot-sdk's plugin approach be simpler for JaiClaw release builds?

## Finding: we already use the exact same plugin

The plugin in the copilot-sdk pom is `org.sonatype.central:central-publishing-maven-plugin` — and JaiClaw's `release` profile (root POM and `jaiclaw-bom`) has used that same plugin since the Central Portal migration. `release.sh` is not an *alternative* to the plugin; it is a wrapper that ultimately runs:

```bash
./mvnw clean deploy -Prelease -s maven-central-deploy/settings-central.xml -DskipTests
```

…which invokes the very plugin the copilot-sdk uses. **There is no migration to do.** The real question becomes: does the copilot-sdk configuration teach us anything that would simplify or harden our flow? Answer: a few small things, plus the analysis surfaced one real bug in our CI workflow.

## Side-by-side comparison

| Aspect | copilot-sdk (`java/pom.xml`) | JaiClaw (root + BOM POMs) |
|---|---|---|
| Publishing plugin | `central-publishing-maven-plugin` **0.10.0**, declared in the **main build** | Same plugin, **0.9.0**, declared in the **`release` profile** (latest available: **0.11.0**, June 2026) |
| `autoPublish` | `true` | `true` |
| `waitUntil` | not set → defaults to `validated` (returns after portal validation) | `published` (blocks until fully published — 10–30 min) |
| `ignorePublishedComponents` | not set | `true` (good — makes partial-failure re-runs resumable) |
| GPG signing | `maven-gpg-plugin` 3.2.8, `verify` phase, in a `release` profile | Same, 3.2.8 in root / **3.2.7 in BOM** (drifted) |
| Sources/javadoc jars | `release` profile | Same (`doclint=none`, `failOnError=false` — needed for our record-heavy code) |
| SNAPSHOT publishing | `distributionManagement.snapshotRepository` → `central.sonatype.com/repository/maven-snapshots/` | `distributionManagement` → TapTech Nexus (`deploy.releases.url` / `deploy.snapshots.url` properties) |
| Release orchestration | CI only (single module, single target) | `release.sh` (versioning, tag, Central + Nexus + Docker) and `publish-central.yml` (tag-driven CI) |

### Why copilot-sdk's layout is simpler *for them* but wrong *for us*

1. **Plugin in main build vs. profile.** They can declare the plugin unconditionally because Central is their only deploy target. JaiClaw's plain `mvn deploy` (no profile) is the TapTech Nexus path — `deploy-nexus.sh` and the second leg of `release.sh` depend on it. Declaring the central plugin with `<extensions>true</extensions>` in the main build would hijack every `deploy` invocation and break the Nexus flow. **Keep the profile.**
2. **No fat-JAR problem.** copilot-sdk is one thin JAR. Our `release` profile additionally sets `spring-boot.repackage.skip=true` because 60–90 MB app fat-JARs blow past Central's bundle upload limit (HTTP 413). That logic only makes sense inside a profile.
3. **No multi-repo, no Docker, no version choreography.** `release.sh` does versioning (`versions:set` twice — root reactor + standalone BOM), commit, tag, Nexus deploy, and Docker image push. The publishing plugin does none of that regardless of where it's declared. The scripts are orchestration, not a substitute publishing mechanism.

**Decision: do not restructure to match copilot-sdk. The plugin approach is validated — we already have it. Adopt the refinements below instead.**

## Bug found during analysis: CI workflow can't authenticate to Central

`.github/workflows/publish-central.yml` loads `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` from 1Password into the job environment, but **nothing maps them into Maven's `<server id="central">`**:

- `setup-java` is called *without* `server-id` / `server-username` / `server-password`, so no `settings.xml` server entry is generated.
- The deploy step runs `./mvnw -B -Prelease deploy -DskipTests` with no `-s` flag.
- The central-publishing plugin resolves credentials **only** via `publishingServerId=central` in `settings.xml` — it does not read arbitrary env vars.

Result: the workflow will fail (401 / missing server) at the upload step. Local releases via `release.sh` work because they pass `-s maven-central-deploy/settings-central.xml`. This should be fixed regardless of anything else in this doc — fix is in Phase 2 below.

## Implementation plan

### Phase 1 — POM alignment (small, zero-risk, do first)

Files: `pom.xml`, `jaiclaw-bom/pom.xml` (remember: BOM is standalone, no parent — every change is made **twice**).

1. Bump `central-publishing-maven-plugin` `0.9.0` → `0.11.0` in both POMs (copilot-sdk is on 0.10.0; 0.11.0 is current as of 2026-06).
2. Align `maven-gpg-plugin` in the BOM `3.2.7` → `3.2.8` to match root.
3. Change `waitUntil` from `published` → `validated` in both POMs. `autoPublish=true` still publishes automatically; the build just stops blocking for the 10–30 min portal publish cycle. Post-release verification (already in `release.sh`'s "next steps" output) covers confirmation. *If you prefer the hard guarantee that the script only exits on a published release, skip this item — it's a trade of feedback for wall-clock time.*
4. Keep `ignorePublishedComponents=true` (copilot-sdk doesn't set it, but it's what makes our large multi-module upload safely re-runnable).
5. Optional hygiene: a shared property is impossible across the standalone BOM, so instead add a comment in both POMs: `<!-- KEEP IN SYNC with jaiclaw-bom/pom.xml (or root pom.xml) release profile -->`.

### Phase 2 — fix `publish-central.yml` credential wiring

Add server config to the existing `setup-java` step:

```yaml
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
          server-id: central                       # matches publishingServerId
          server-username: MAVEN_CENTRAL_USERNAME  # env var NAME, not value
          server-password: MAVEN_CENTRAL_PASSWORD  # env var NAME, not value
```

`setup-java` writes a `settings.xml` whose server entry references `${env.MAVEN_CENTRAL_USERNAME}` / `${env.MAVEN_CENTRAL_PASSWORD}`; the 1Password step already exports those into the job env (`export-env: true`), so the deploy step resolves them at run time. No secrets land on disk.

Note the ordering constraint: `setup-java` only needs to run before the deploy step — the 1Password load can stay where it is.

### Phase 3 (optional) — Central SNAPSHOT publishing, copilot-sdk style

copilot-sdk points its `snapshotRepository` at `https://central.sonatype.com/repository/maven-snapshots/`. If we ever want public SNAPSHOTs (e.g. so external users can test pre-releases without TapTech Nexus access):

1. Enable SNAPSHOTs for the `io.jaiclaw` namespace on the Central Portal (Namespaces → `io.jaiclaw` → Enable SNAPSHOTs).
2. Add a dedicated profile (do **not** touch the default `distributionManagement`, which is the Nexus path):

```xml
<profile>
    <id>central-snapshots</id>
    <distributionManagement>
        <snapshotRepository>
            <id>central</id>
            <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        </snapshotRepository>
    </distributionManagement>
</profile>
```

3. Publish with `./mvnw deploy -Pcentral-snapshots -DskipTests` (no signing/javadoc needed for SNAPSHOTs). Add to both root and BOM if the BOM should ship snapshots too.

This is additive; TapTech Nexus stays the default snapshot home. Skip this phase entirely if internal Nexus snapshots are sufficient.

### Phase 4 — verification

1. `cd maven-central-deploy && ./05-verify-setup.sh` — confirm setup still green after POM changes.
2. `./dry-run.sh` — full `verify -Prelease` build: confirms 0.11.0 plugin resolves, artifacts sign, sources/javadoc attach, fat JARs still skipped.
3. Next real release: run `./release.sh <version>` as usual; confirm the deployment shows **VALIDATED → PUBLISHING → PUBLISHED** on [central.sonatype.com/publishing](https://central.sonatype.com) and artifacts appear at `repo1.maven.org/maven2/io/jaiclaw/`.
4. CI path: after Phase 2, push the release tag (or `workflow_dispatch`) and confirm `publish-central.yml` reaches the portal upload — this has likely never succeeded before, so treat its first green run as the acceptance test.

## What we are explicitly NOT doing

- **Not** moving `central-publishing-maven-plugin` into the main `<build>` (breaks the Nexus `mvn deploy` path and every plain deploy).
- **Not** adopting `maven-release-plugin` — `release.sh` + `versions:set` already covers version choreography with less ceremony.
- **Not** retiring `maven-central-deploy/` — the scripts orchestrate things the publishing plugin cannot (dual-target deploy, Docker push, tagging, version bump). The long-term direction is the tag-driven CI workflow as primary and `release.sh` as the local fallback, but that's a process choice, not a plugin one.

## References

- copilot-sdk pom: https://github.com/github/copilot-sdk/blob/main/java/pom.xml
- Plugin docs: https://central.sonatype.org/publish/publish-portal-maven/
- Plugin versions: 0.11.0 latest (2026-06-16); we're on 0.9.0
- Local flow: `maven-central-deploy/README.md`, `maven-central-deploy/DEPLOY-TO-MAVEN-CENTRAL.md`
- CI: `.github/workflows/publish-central.yml` (credential wiring bug — Phase 2)
