# Phase 6 — P2 Observability & Knowledge — Progress Tracker

**Started:** —
**Status:** Not Started
**Depends on:** Phase 2 complete

## Work Items

### 6.1 Trajectory Tracking
- **Location:** Enhance existing `extensions/jaiclaw-audit/` (NOT a new module)
- **Status:** NOT STARTED
- **Revised estimate:** 3-5 days (down from 1-2 weeks)
- **Approach:** Extend existing `AuditLogger` SPI and `AuditEvent` in `jaiclaw-audit` rather than creating a new module. Micrometer tracing (from 1.5) provides timing/trace correlation. The semantic content (which tool was called, what the LLM decided, token usage) is custom recording on top of the audit infrastructure.
- **Library:** Micrometer Tracing (already added in Phase 1.5) provides the trace ID / span ID skeleton.
- **What already exists:**
  - `AuditLogger` SPI in `jaiclaw-audit`
  - `AuditEvent` record
  - `InMemoryAuditLogger` implementation
  - Micrometer tracing (after 1.5 is done) for timing correlation
- **What to build:**
  - [ ] Extend `AuditEvent` with trajectory-specific fields (step type, tool name, input/output summary, token count, duration)
  - [ ] `TrajectoryRecorder.java` — instruments `AgentRuntime` to emit trajectory events at each decision point (~100-150 lines)
  - [ ] `FileAuditLogger.java` — persistent `AuditLogger` implementation that writes JSON-lines files (~100 lines)
  - [ ] Bundle manifest support for exporting traces
  - [ ] Spock specs
- **No need to build:** New `extensions/jaiclaw-trajectory/` module, new SPI, new starter — extend existing audit module.
- **Configuration:**
  ```yaml
  jaiclaw:
    audit:
      trajectory:
        enabled: true
        store-dir: ${JAICLAW_TRAJECTORY_DIR:./trajectories}
        retention-days: 30
  ```
- **Notes:** ~200-300 lines. Builds on existing `jaiclaw-audit` infrastructure.

### 6.2 Transcript Storage
- **Location:** Enhance existing `extensions/jaiclaw-audit/` or minimal new extension
- **Status:** NOT STARTED
- **Revised estimate:** half day (down from 1-3 days)
- **Approach:** Simple Jackson serialization + file I/O. No external library needed. Could be part of `jaiclaw-audit` (transcripts are a form of audit trail) or a small standalone extension.
- **No library needed.** Jackson (already a dependency) + `java.nio.file` API.
- **What to build:**
  - [ ] `TranscriptSession.java`, `TranscriptUtterance.java` (records — ~30 lines)
  - [ ] `TranscriptStore.java` (SPI — ~20 lines)
  - [ ] `FileTranscriptStore.java` (JSON file storage — ~60-80 lines)
  - [ ] `TranscriptSummaryRenderer.java` (Markdown rendering — ~40 lines)
  - [ ] Config + auto-configuration
  - [ ] Spock specs
- **File storage layout:**
  ```
  {transcripts-dir}/
    2026-05-28/
      {sessionId}/
        session.json
        utterances.jsonl
  ```
- **Configuration:**
  ```yaml
  jaiclaw:
    transcripts:
      enabled: true
      store-dir: ${JAICLAW_TRANSCRIPTS_DIR:./transcripts}
      retention-days: 90
  ```
- **Notes:** ~100-150 lines total. Pure file I/O.

### 6.3 Internationalization (i18n)
- **Location:** Enhance `core/jaiclaw-core/`
- **Status:** NOT STARTED
- **Revised estimate:** 3-5 days (unchanged — effort is string extraction/translation, not code)
- **Approach:** 100% covered by built-in Java/Spring:
  - `core/jaiclaw-core` (pure Java, no Spring) → use `java.util.ResourceBundle.getBundle()` directly
  - Spring modules → use `MessageSource` which is auto-configured by Spring Boot when `messages.properties` exists
  - **The actual work is identifying user-facing strings and creating translation files, not writing library code.**
- **No library needed.** `java.util.ResourceBundle` (JDK built-in) + Spring `MessageSource` (already in Spring context).
- **Files to create:**
  - [ ] `core/jaiclaw-core/src/main/java/io/jaiclaw/core/i18n/JaiClawMessages.java` — typed accessor using `ResourceBundle`
  - [ ] `core/jaiclaw-core/src/main/java/io/jaiclaw/core/i18n/JaiClawLocale.java` — supported locale enum
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages.properties` (English default)
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_zh_CN.properties`
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_es.properties`
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_pt_BR.properties`
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_de.properties`
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_fr.properties`
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_ja.properties`
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_ko.properties`
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_ar.properties`
  - [ ] `core/jaiclaw-core/src/main/resources/i18n/messages_tr.properties`
  - [ ] Spock specs
- **Key consideration:** `jaiclaw-core` has zero Spring dependency. `JaiClawMessages` uses `java.util.ResourceBundle` directly. A Spring-based `MessageSource` wrapper can live in the starter.
- **Scope of translations:**
  - Tool descriptions
  - Error messages
  - Status text
  - System prompt sections
  - Channel-facing labels
- **Notes:** Most time spent on string extraction and translation, not code.

### 6.4 Secrets Management
- **Location:** Starter POM only (NO new module, NO custom SPI)
- **Status:** NOT STARTED
- **Revised estimate:** 1 day (down from 1-2 weeks)
- **Approach:** **Just add dependency + YAML config — zero code.** Two excellent, complementary options already exist:
- **Option A (recommended): Spring Cloud Vault**
  - `spring-cloud-starter-vault-config` mounts HashiCorp Vault secrets as Spring property sources
  - Supports token, AppRole, and Kubernetes authentication
  - Secrets appear as `@Value("${my.secret}")` properties
  - Auto-configured. Zero code changes.
  ```xml
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-vault-config</artifactId>
  </dependency>
  ```
  ```yaml
  spring.cloud.vault:
    uri: http://localhost:8200
    token: ${VAULT_TOKEN}
    kv.backend: secret
  ```

- **Option B: Camel Vault Components (already using Camel 4.18.1)**
  - `camel-hashicorp-vault`, `camel-aws-secrets-manager`, `camel-azure-key-vault`, `camel-google-secret-manager`
  - Use property placeholder syntax: `{{hashicorp:mySecret}}` in Camel routes
  - Auto-refresh on secret rotation
  - Just add the appropriate `camel-*-vault` dependency
  ```xml
  <dependency>
      <groupId>org.apache.camel</groupId>
      <artifactId>camel-hashicorp-vault</artifactId>
  </dependency>
  ```

- **What to build:**
  - [ ] Starter POM: `jaiclaw-starters/jaiclaw-starter-secrets/pom.xml` (dependency aggregator)
  - [ ] Register starter in `jaiclaw-starters/pom.xml`
  - [ ] Documentation for configuration
- **No need to build:** `SecretStore` SPI, `EnvironmentSecretStore`, `EncryptedFileSecretStore`, `VaultSecretStore`, `SecretReference`, `SecretAuditEntry`, auto-configuration class — Spring Cloud Vault and Camel Vault handle everything transparently.
- **Notes:** Zero lines of Java code. The starter is a POM-only dependency aggregator.

## Session Log

### Session 1 — 2026-05-28
- Revised all items to leverage existing libraries
  - 6.1 Trajectory: Extend existing jaiclaw-audit → 3-5 days (was 1-2 weeks), no new module
  - 6.2 Transcripts: Pure Jackson file I/O → half day (was 1-3 days)
  - 6.4 Secrets: Spring Cloud Vault or Camel Vault → 1 day (was 1-2 weeks), zero code
  - 6.3 i18n: Unchanged — java.util.ResourceBundle, effort is translation not code
