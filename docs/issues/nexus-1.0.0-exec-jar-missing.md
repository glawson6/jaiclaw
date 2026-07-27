# Nexus lost `jaiclaw-cli-1.0.0-exec.jar` during the 1.0.0 deploy

**Area:** `maven-central-deploy/deploy-nexus.sh` + TapTech Nexus (`tooling.taptech.net`) + `install.sh` (adopter-facing)
**Severity:** ships-broken for the `curl -fsSL https://jaiclaw.io/install.sh | bash` adopter path — 404 on the CLI fat jar
**Trigger:** post-deploy verification of the 1.0.0 Nexus artifacts (2026-07-27) — pom + thin jar (20 KB) present, `-exec.jar` (158 MB fat jar) returns HTTP 404 despite Maven client reporting the upload as complete

## Reproduction

```bash
# All three commands should return 200; the third returns 404
curl -sI https://tooling.taptech.net/repository/maven-releases/io/jaiclaw/jaiclaw-cli/1.0.0/jaiclaw-cli-1.0.0.pom      # → 200
curl -sI https://tooling.taptech.net/repository/maven-releases/io/jaiclaw/jaiclaw-cli/1.0.0/jaiclaw-cli-1.0.0.jar      # → 200
curl -sI https://tooling.taptech.net/repository/maven-releases/io/jaiclaw/jaiclaw-cli/1.0.0/jaiclaw-cli-1.0.0-exec.jar # → 404
```

Consequence: `curl -fsSL https://jaiclaw.io/install.sh | bash` fails at the download step for any adopter targeting 1.0.0.

## Deploy log evidence

The 2026-07-25 deploy log (`/tmp/uat-1.0.0-nexus-deploy.log`, run during the 1.0.0 release UAT sweep) shows:

```
[INFO] --- deploy:3.1.4:deploy (default-deploy) @ jaiclaw-cli ---
Uploading to taptech-repo: https://tooling.taptech.net/repository/maven-releases/io/jaiclaw/jaiclaw-cli/1.0.0/jaiclaw-cli-1.0.0.pom
Uploaded to taptech-repo: ...jaiclaw-cli-1.0.0.pom (12 kB at 60 kB/s)
Uploading to taptech-repo: ...jaiclaw-cli-1.0.0.jar
Uploaded to taptech-repo: ...jaiclaw-cli-1.0.0.jar (21 kB at 57 kB/s)
Uploading to taptech-repo: ...jaiclaw-cli-1.0.0-exec.jar
Progress (2): 21 kB | 0/158 MB → ... → 158 MB
Uploaded to taptech-repo: ...jaiclaw-cli-1.0.0-exec.jar (158 MB at 9.1 MB/s)
```

Every phase of the upload reports success — no HTTP error, no timeout, no interrupted connection. The client-side upload completes cleanly. Yet the file is not retrievable afterward.

## Local-side sanity

The local artifact IS on disk with the correct size:

```bash
$ ls -la ~/.m2/repository/io/jaiclaw/jaiclaw-cli/1.0.0/
-rw-r--r--  1 tap  staff  158411452 Jul 25 23:50 jaiclaw-cli-1.0.0-exec.jar
-rw-r--r--  1 tap  staff      20729 Jul 25 22:32 jaiclaw-cli-1.0.0.jar
-rw-r--r--  1 tap  staff      12112 Jul 25 22:30 jaiclaw-cli-1.0.0.pom
```

So the artifact was built correctly and Maven attempted the upload with the correct bytes. The break is server-side.

## Suspected root cause

One of:

1. **Nexus blob-storage quota** — the storage tier for `maven-releases` may have a per-file size cap or a total-storage quota that was exceeded. Nexus's default policy on quota-full is inconsistent (some versions return 507, others return 200 and drop the write).
2. **nginx `client_max_body_size`** — the reverse proxy in front of Nexus may cap request bodies at, e.g., 100 MB. Some nginx configurations will 413 immediately; others (with `client_body_in_file_only` or upstream buffering off) may accept the request, discard the body, and let Nexus report 200 on the incomplete write.
3. **Nexus policy hook / cleanup task** — a Nexus scheduled task ("Compact blob store", "Purge unused blobs", "Remove non-Maven artifacts") could have deleted the artifact after the deploy. The pom + thin jar were retained because they matched a Maven-shape filter; the classified `-exec.jar` may not have.
4. **Bug in Maven's `deploy` plugin under Boot 4** — theoretical, but low-probability given the 21 KB `-original` and thin jars uploaded fine.

Root cause needs admin-side inspection of the Nexus event log, blob store metrics, and nginx access log.

## Fix path

Because 1.0.0 is a release tag under Nexus's release-repository immutability rules, the artifact cannot be republished under the same coordinates. Fix ships as **1.0.1**:

1. **Diagnose first** — before deploying 1.0.1, resolve the underlying cause. Otherwise the same silent-drop will happen again.
   - Check Nexus web UI → Repository → maven-releases → Content selectors + Storage → Blob store diagnostics
   - Check nginx access log for the specific `jaiclaw-cli-1.0.0-exec.jar` PUT — status code + bytes-sent
   - Check the tooling.taptech.net operator's storage quota
   - Consider whether the fat jar is worth uploading at all (adopters can build from source in ~2 min; the 158 MB blob is a Nexus cost with limited benefit vs `setup.sh --from-source`)
2. **Add a post-deploy verification step to `deploy-nexus.sh`** — after `mvn deploy` completes, `curl -I` each expected artifact and assert 200. Fail the script + email the operator if any artifact is missing from Nexus. Prevents the same silent-drop from shipping unnoticed in future releases.
3. **Republish under 1.0.1** — either fixed upload path, or accept the fat jar isn't going to Nexus and permanently remove the `curl | bash` promise from the docs.

## Adopter-visible impact (documented in 1.0.0 docs)

- `README.md` — `curl | bash` path now has a warning banner. Adopters routed to Docker (`quickstart.sh`) or source (`setup.sh`).
- `install.sh` — download-failure branch now prints a targeted message for the 1.0.0 case + points at `--from-source`.
- `CHANGELOG.md` `[Unreleased]` — first item in the 1.0.1 planned-fix list.

## Not in scope

- **Republishing 1.0.0** — the release-tag immutability makes this impossible without a Nexus admin manually deleting the tag from `maven-releases`, which would rewrite adopter-visible history. Better to just ship 1.0.1.
- **Uploading the artifact via a manual `curl PUT`** — considered; even if it succeeds against the raw HTTP API (bypassing the deploy plugin's attach mechanism), it doesn't fix the underlying cause and could mask the recurrence in 1.0.1.
- **Fixing 0.9.3 or earlier** — those releases are unaffected; `curl | bash` works against them.
