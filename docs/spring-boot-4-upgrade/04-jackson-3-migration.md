# 04 — Jackson 2 → Jackson 3 Migration

> The single largest mechanical change: **225 files** import `com.fasterxml.jackson`; **42 poms** declare `jackson-databind`; **13 poms** declare `jackson-datatype-jsr310`; **5** `jackson-dataformat-yaml`; **2** `jjwt-jackson`.
> Sources: [Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) · [Introducing Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)

## 1. The rules

| Jackson 2 | Jackson 3 |
|---|---|
| groupId `com.fasterxml.jackson.core` etc. | **`tools.jackson.core`** etc. |
| package `com.fasterxml.jackson.databind.*` | **`tools.jackson.databind.*`** |
| **EXCEPTION:** `jackson-annotations` | **unchanged** — stays groupId `com.fasterxml.jackson.core`, package `com.fasterxml.jackson.annotation.*` (`@JsonProperty`, `@JsonIgnore`, `@JsonCreator`, …) |
| `ObjectMapper` (mutable) | `JsonMapper` (immutable) via `JsonMapper.builder()....build()`; `ObjectMapper` exists as the format-agnostic base — prefer `JsonMapper` for JSON |
| `mapper.registerModule(new JavaTimeModule())` | **built in** — JSR-310 (and parameter-names) support is part of Jackson 3 core; delete the registration AND the 13 `jackson-datatype-jsr310` pom entries |
| checked `IOException`/`JsonProcessingException` | unchecked `JacksonException` (and `IllegalArgumentException` in some paths) — **catch blocks change**; code that *only* compiled because of checked-exception propagation will get "unreachable catch" errors |
| `JsonDeserializer`/`JsonSerializer` (databind) | `ValueDeserializer`/`ValueSerializer` |
| `Jackson2ObjectMapperBuilder` (Spring) | **removed** — use `JsonMapper.builder()`; Boot customization via `JsonMapperBuilderCustomizer` (was `Jackson2ObjectMapperBuilderCustomizer`) |
| `@JsonComponent` / `@JsonMixin` (Boot) | `@JacksonComponent` / `@JacksonMixin` |

**Behavioral default changes in Jackson 3** (audit, don't sleepwalk): `FAIL_ON_UNKNOWN_PROPERTIES` **off** by default; `WRITE_DATES_AS_TIMESTAMPS` **off** by default; several others. Boot offers `spring.jackson.use-jackson2-defaults=true` as a compatibility switch — see §4.

**Boot property renames**: `spring.jackson.{read,write,parser}.*` → `spring.jackson.json.{read,write}.*` (4.1 re-adds cross-format `spring.jackson.read/write.*`). Grep ymls for `spring.jackson`.

## 2. Repo strategy

Order: after the Phase-1 pom wave (Boot 4.1 BOM in place), Jackson 3 lands as **Phase 2**, module-by-module in dependency order (`jaiclaw-core` → `channel-api` → `tools` → … per the graph in CLAUDE.md), keeping each `-pl` compile green before moving on.

1. **Poms (42 + 13 + 5):** flip `com.fasterxml.jackson.core:jackson-databind` → `tools.jackson.core:jackson-databind` (version from Boot BOM — drop explicit versions); **delete** `jackson-datatype-jsr310` entries; `jackson-dataformat-yaml` → `tools.jackson.dataformat:jackson-dataformat-yaml`. Leave `jackson-annotations` entries alone.
2. **Imports (225 files):** mechanical rewrite `import com.fasterxml.jackson.` → `import tools.jackson.` **except** `com.fasterxml.jackson.annotation.` — a one-line guard in the sed/rewrite makes this safe:
   ```bash
   # preview scope
   git grep -l "com.fasterxml.jackson" -- '*.java' '*.groovy' \
     | xargs grep -l "com.fasterxml.jackson\.\(core\|databind\|dataformat\|datatype\|module\)"
   ```
   Prefer the OpenRewrite Jackson-3 recipes (bundled in the Boot-4 recipe chain) over raw sed — they also handle `ObjectMapper`→builder patterns and exception types.
3. **`new ObjectMapper()` sites:** replace with `JsonMapper.builder().build()` (or inject Boot's auto-configured mapper — preferred in Spring modules; keep manual construction only in `jaiclaw-core`, which must stay Spring-free).
4. **`registerModule`/`findAndRegisterModules` sites:** delete JavaTime/ParameterNames registrations; audit any custom modules.
5. **Exception handling:** grep `JsonProcessingException|JsonMappingException|catch (IOException` near Jackson call sites; convert to `JacksonException` handling. Watch for now-dead catch blocks failing compilation.
6. **Known JaiClaw hotspots** (JSON-lines persistence and wire formats — behavior, not just compile):
   - `FileAuditLogger` / `HashChainedAuditLogger` (audit JSON-lines; **chain hashes are computed over serialized JSON** — serialization-order/format drift would break `verifyChain(tenantId)` on pre-migration files: add a cross-version spec fixture!)
   - `TranscriptStore` file archival, cron JSON persistence, identity JSON persistence, kanban `EffectLedger`/jsonl journal, docstore metadata
   - MCP server JSON-RPC surfaces (`/mcp/*`), tool schema generation (`SchemaBuilder`), pipeline templating metadata
   - Skills/plugin manifest parsing (YAML via jackson-dataformat-yaml in loader paths)
7. **Third-party boundary check:** libraries that still consume Jackson 2 (jjwt-jackson ×2 poms, possibly line-bot-sdk, github-api, Drools) keep working because **Boot 4 still manages Jackson 2.21.x "in deprecated form"** — Jackson 2 and 3 coexist on the classpath (different packages). Do NOT try to purge Jackson 2 from the dependency tree; just ensure *our* code is Jackson 3. Framework 7's Jackson-2 auto-detection dies in 7.2 — the bridge is temporary for Spring-integration points only.

## 3. The Embabel/Spring AI boundary

- Embabel's Boot-4 line is Jackson 3 (`tools.jackson`) — its exposed `ObjectMapper` (platform services) will be a Jackson 3 type. `jaiclaw-embabel-delegate`'s goal-result serialization must be Jackson 3 (Phase 4 dependency on this phase).
- Spring AI 2.0 replaces its `JsonParser`/`ModelOptionsUtils` JSON helpers with `JsonHelper` — [05 §6](05-spring-ai-2-migration.md).

## 4. Decision needed at execution time (record in README decision log)

**`spring.jackson.use-jackson2-defaults`**: recommend **false** (adopt Jackson 3 defaults — we control both ends of our persisted formats and the compat switch will eventually go away), BUT only after the §2.6 hotspot fixtures prove: (a) old audit chains still verify, (b) old transcripts/cron/identity/kanban files still deserialize, (c) MCP/tool JSON wire shapes are unchanged where external clients depend on them (dates-as-timestamps and unknown-properties are the classic silent breakers). If fixtures reveal drift, ship 1.0.0 with `use-jackson2-defaults=true` + explicit per-mapper feature flags, and schedule the default-flip for 1.1.

## 5. Verification

```bash
# zero non-annotation Jackson-2 imports in our source
git grep -n "import com.fasterxml.jackson" -- '*.java' '*.groovy' | grep -v ".annotation." | wc -l   # must be 0
# zero jackson 2 core/databind in our poms (annotations + jjwt bridge excepted)
git grep -n "com.fasterxml.jackson" -- '*/pom.xml' | grep -v annotations
# round-trip fixtures
./mvnw test -pl :jaiclaw-audit,:jaiclaw-cron,:jaiclaw-identity,:jaiclaw-kanban,:jaiclaw-docstore -o
```
