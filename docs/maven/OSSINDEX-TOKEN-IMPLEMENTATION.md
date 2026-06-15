# OSS Index Authentication — Implementation Guide

**Project:** JaiClaw
**Component:** OWASP Dependency-Check (Maven plugin) — Sonatype OSS Index analyzer
**Audience:** Build/CI maintainers
**Last updated:** June 15, 2026

---

## 1. Problem

The build fails (or the report silently drops OSS Index findings) at the Sonatype OSS Index analyzer step. Symptoms include `401 Unauthorized`, `403 Forbidden`, or a log line indicating the analyzer was disabled.

This is **not a misconfiguration on your side** — it is a policy change at Sonatype:

- **September 2025** — OSS Index began enforcing API tokens for authentication. Anonymous and password-based access stopped working reliably.
- **April 2026** — A migration to *Sonatype Guide* tokens began, planned to replace the legacy OSS Index API keys/tokens before the end of 2026.
- When no valid credentials are supplied, Dependency-Check **automatically disables** the OSS Index analyzer.

The OSS Index analyzer is supplementary to the primary NVD data source, so you have a real choice between authenticating, degrading gracefully, or disabling it.

---

## 2. Decision: Pick One Path

| Path | When to use | Build stability during 2026 migration |
|------|-------------|----------------------------------------|
| **A — Authenticate** | You want OSS Index coverage in reports | Medium (token model is in transition) |
| **B — Warn-only** | You want coverage but cannot tolerate CI breakage | High |
| **C — Disable** | NVD coverage is sufficient for your risk posture | High |

Recommendation for CI through end of 2026: **Path A + Path B together** — authenticate, but set remote errors to warn-only so token/migration churn never fails the pipeline.

---

## 3. Path A — Authenticate

### 3.1 Obtain a token

1. Create a free account at `ossindex.sonatype.org` (or sign in to your existing Sonatype account, since the Guide migration is consolidating these).
2. Open account settings and generate an **API token**.
3. Note the two values you will need:
   - **Username** = your account email address
   - **Password** = the **API token** (not your login password)

### 3.2 Maven plugin properties

The plugin reads two properties:

- `ossIndexUsername`
- `ossIndexPassword` (set this to the API token)

**Never hardcode the token.** Reference externalized properties or environment variables only.

### 3.3 `pom.xml` configuration

```xml
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <configuration>
    <ossIndexAnalyzerEnabled>true</ossIndexAnalyzerEnabled>
    <ossIndexUsername>${ossIndex.user}</ossIndexUsername>
    <ossIndexPassword>${ossIndex.token}</ossIndexPassword>
    <!-- See Path B: do not fail the build on transient remote errors -->
    <ossIndexAnalyzerWarnOnlyOnRemoteErrors>true</ossIndexAnalyzerWarnOnlyOnRemoteErrors>
  </configuration>
</plugin>
```

The `${ossIndex.user}` / `${ossIndex.token}` properties are supplied at invocation time (next section) so secrets never live in the repo.

### 3.4 Command-line invocation

```bash
./mvnw org.owasp:dependency-check-maven:check \
  -DossIndexUsername="$OSS_USERNAME" \
  -DossIndexPassword="$OSS_API_TOKEN"
```

---

## 4. GitHub Actions Integration

### 4.1 Store the secrets

In the JaiClaw repository: **Settings → Secrets and variables → Actions → New repository secret**

- `OSS_USERNAME` — your OSS Index account email
- `OSS_API_TOKEN` — the generated API token

### 4.2 Workflow step

```yaml
  dependency-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: OWASP Dependency-Check
        env:
          OSS_USERNAME: ${{ secrets.OSS_USERNAME }}
          OSS_API_TOKEN: ${{ secrets.OSS_API_TOKEN }}
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
        run: |
          ./mvnw -B org.owasp:dependency-check-maven:check \
            -DossIndexUsername="$OSS_USERNAME" \
            -DossIndexPassword="$OSS_API_TOKEN" \
            -DnvdApiKey="$NVD_API_KEY" \
            -DdataDirectory="${{ github.workspace }}/.dc-data"
```

### 4.3 Cache the NVD data directory (recommended)

The NVD download is slow and rate-limited; caching the `dataDirectory` between runs avoids `403`s on the NVD side and cuts build time substantially.

```yaml
      - name: Cache Dependency-Check data
        uses: actions/cache@v4
        with:
          path: ${{ github.workspace }}/.dc-data
          key: dc-data-${{ runner.os }}-${{ github.run_id }}
          restore-keys: |
            dc-data-${{ runner.os }}-
```

> Note: an NVD API key (`NVD_API_KEY`) is a separate credential from the OSS Index token. With a single key across many concurrent builds you can still hit NVD rate limits and get `403` errors — caching the data directory is the mitigation.

---

## 5. Path B — Warn-Only on Remote Errors

Lets remote failures (token issues, migration hiccups, rate limits) emit a warning instead of failing the build.

**`pom.xml`:**

```xml
<ossIndexAnalyzerWarnOnlyOnRemoteErrors>true</ossIndexAnalyzerWarnOnlyOnRemoteErrors>
```

**Command line:**

```bash
-DossIndexRemoteErrorWarnOnly=true
```

---

## 6. Path C — Disable OSS Index Entirely

If NVD coverage is sufficient:

**`pom.xml`:**

```xml
<ossIndexAnalyzerEnabled>false</ossIndexAnalyzerEnabled>
```

**Command line:**

```bash
-DossIndexAnalyzerEnabled=false
```

---

## 7. Verification

After applying the change, run locally:

```bash
./mvnw org.owasp:dependency-check-maven:check \
  -DossIndexUsername="$OSS_USERNAME" \
  -DossIndexPassword="$OSS_API_TOKEN"
```

A healthy run logs:

```
[INFO] Finished Sonatype OSS Index Analyzer (N seconds)
```

Failure modes:

- `Invalid credentials for the OSS Index, disabling the analyzer` → the token/username pair is wrong, or you passed your login password instead of the token.
- Analyzer absent from the log entirely → no credentials supplied, auto-disabled.

---

## 8. Known Issue — CLI Works but Maven/Gradle Fails

A token that authenticates correctly with the Dependency-Check **CLI** can return `401` on the **Maven and Gradle plugins** using the same credentials. This is tracked upstream as DependencyCheck issue #7971.

If you have verified the token works elsewhere but the Maven plugin still rejects it:

1. Confirm you are on a **recent** `dependency-check-maven` version.
2. Confirm the token is in `ossIndexPassword`, not `ossIndexUsername`.
3. As a fallback, apply **Path B** (warn-only) so the pipeline stays green while the upstream issue is resolved.

---

## 9. Migration Watch (through end of 2026)

The legacy OSS Index token model is being replaced by Sonatype Guide tokens. Action items:

- Treat the OSS Index token as **temporary** — expect to reissue a Guide token before the end of 2026.
- Keep credentials in CI secrets (already the case above) so rotation is a one-place change.
- Keeping **Path B** active throughout the migration is the lowest-risk posture for build stability.

---

## 10. Quick Reference

| Property (CLI `-D`) | `pom.xml` element | Purpose |
|---------------------|-------------------|---------|
| `ossIndexUsername` | `<ossIndexUsername>` | Account email |
| `ossIndexPassword` | `<ossIndexPassword>` | **API token** |
| `ossIndexAnalyzerEnabled` | `<ossIndexAnalyzerEnabled>` | Toggle analyzer on/off |
| `ossIndexRemoteErrorWarnOnly` | `<ossIndexAnalyzerWarnOnlyOnRemoteErrors>` | Don't fail build on remote errors |
| `nvdApiKey` | `<nvdApiKey>` | Separate NVD credential |
| `dataDirectory` | `<dataDirectory>` | Cache target for NVD data |
