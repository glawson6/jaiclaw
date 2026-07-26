# Pipeline Processor Catalog

The `jaiclaw-pipeline-processors` module (Phase 4 of the Pipeline
Studio buildout) ships the baseline palette every Studio installation
sees on day one — **~15 core processors** across Transform / Control /
Data / Integration, **7 AI presets** for canned agent stages, and
**6 Camel-URI templates** for common integration destinations. Every
entry surfaces automatically in `GET /api/pipeline-studio/catalog` and
in the SPA palette when this module is on the classpath.

## Install

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-pipeline-processors</artifactId>
</dependency>
```

Opt-in via `jaiclaw.pipeline.processors.enabled=true`
(default). Integration processors that depend on the optional
`jaiclaw-documents`, `jaiclaw-memory`, or `jaiclaw-tools` modules
only register when those modules are on the classpath.

---

## Tier 1 — Core processors

Every core processor implements
[`ConfigurableStageProcessor`](../../dev/pipeline/PIPELINE-STUDIO-PROCESSORS-SPI.md)
and carries `@PipelineProcessor` metadata.

### Transform

| Node | Category | Config keys | Notes |
|---|---|---|---|
| **Template** | Transform | `template` | Wraps `TemplateResolver`. Placeholders: `{{stages.X.output}}`, `{{stages.X.metadata.k}}`, `{{input}}`, `{{pipeline.*}}` |
| **Regex Extract** | Transform | `pattern`, `group` (default 0), `allMatches` (default false) | Java regex; `allMatches=true` newline-joins every hit |
| **Regex Replace** | Transform | `pattern`, `replacement` | Global replace; supports `$1`/`$2` backrefs |
| **Trim / Case / Truncate** | Transform | `trim`, `case` (none/upper/lower/title), `maxLength` | All three optional; apply in order |
| **JSON Path Extract** | Transform | `path` (JSONPath), `default` | Uses jayway/JSONPath; missing paths fall back to `default` |
| **JSON Validate** | Validate | `schema` (JSON Schema Draft 2020-12) | Throws → trips pipeline error strategy on mismatch |
| **JSON ↔ CSV** | Transform | `direction` (jsonToCsv/csvToJson), `delimiter`, `headers` | Round-trips array-of-objects ↔ CSV |
| **XML → JSON** | Transform | — | Jackson XmlMapper → JSON tree |
| **HTML → Text** | Transform | — | Strips `<script>`/`<style>`/tags, decodes common entities |
| **Markdown → HTML** | Transform | — | Tiny in-house renderer — headings, bold, italic, code, links |
| **Chunk / Split** | Transform | `maxSize` (default 1000), `overlap` (default 0) | Newline-joins chunks with `---chunk---` separator |

### Control

| Node | Category | Config keys | Notes |
|---|---|---|---|
| **Filter Gate** | Control | `kind` (regex/jsonpath/contains), `predicate`, `onFail` (stop-silently/error) | On stop-silently, sets `exchange.getProperty("jaiclaw.filter.stopped") = true` |
| **Set Metadata** | Control | `key`, `value` (template) | Writes to `PipelineContext.StageOutput.metadata` — read downstream via `{{stages.<this>.metadata.<key>}}` |

### Data

| Node | Category | Config keys | Notes |
|---|---|---|---|
| **HTTP Fetch** | Data | `url` (template), `method` (GET/POST/PUT), `headers` (newline-separated), `body` (template), `timeoutSeconds` | Only `http`/`https` schemes; distinct from raw CAMEL for safety |
| **File Read** | Data | `path` (template) | Tenant-prefixed base dir in MULTI mode; refuses `../` path escape |
| **File Write** | Data | `path` (template) | Same tenant rules; writes UTF-8 |

---

## Tier 2 — AI presets

Canned `AGENT` stages loaded from `META-INF/jaiclaw-pipeline-presets/*.yml`.
Each preset carries a prompt template with `{{config.field}}`
placeholders the Studio inspector fills at insert-time.

| Preset | Purpose | Config keys |
|---|---|---|
| **Summarize** | Boil down input into a concise summary | `style` (concise/formal/casual/bullet-points), `length` |
| **Classify** | Assign one of a fixed set of labels | `labels` (comma-separated) |
| **Extract to JSON** | Extract fields into a target JSON shape | `schema` (JSON Schema or example object) |
| **Translate** | Translate to a target language | `targetLanguage` |
| **Sentiment** | Score input as positive/neutral/negative | — |
| **Redact PII** | Replace emails/phones/names/addresses/cards with placeholders | — |
| **Draft Reply** | Draft a reply in a specified tone | `tone`, `length`, `signoff` |

Adopters ship additional presets by dropping YAMLs into any
classpath location matching
`META-INF/jaiclaw-pipeline-presets/*.yml` — no code required.

---

## Tier 3 — Camel templates + integration processors

### Camel-URI templates

Curated `CAMEL`-stage parameterised URI patterns loaded from
`META-INF/jaiclaw-pipeline-camel-templates/*.yml`. Studio substitutes
`{{config.field}}` at insert-time; the resulting stage `uri` is
subject to the Phase 3 URI-scheme allowlist.

| Template | Scheme | Purpose | Config keys |
|---|---|---|---|
| **Send Email (SMTP)** | `smtp` | Send via SMTP | `host`, `port`, `username`, `password`, `from`, `to`, `subject` |
| **Kafka Publish** | `kafka` | Publish to a Kafka topic | `topic`, `brokers` |
| **HTTP POST (webhook)** | `http` | POST body to an HTTP endpoint | `host`, `path` |
| **JDBC Query** | `jdbc` | Run SQL against a Spring DataSource | `dataSourceRef` |
| **S3 File Archive** | `aws2-s3` | Write body to an S3 key | `bucket`, `region`, `keyName` |
| **Log** | `log` | Log the body via Camel `log:` | `category`, `level` |

**Allowlist reminder:** UI-authored pipelines only run after the
`PipelineSecurityProperties.allowedUriSchemes` gate — the default
list (`direct, seda, log, vm, timer, quartz`) covers Log; adopters
extend the list to enable Kafka / SMTP / etc. per deployment.

### Integration processors

| Node | Category | Requires | Notes |
|---|---|---|---|
| **Tool Invoke** | Integration | `ToolRegistry` bean | Runs any tool from the registry as a stage; config = `tool` name + `args` JSON template. Every JaiClaw + MCP tool becomes a pipeline node for free |
| **Memory Search** | Integration | `MemorySearchManager` bean (jaiclaw-memory) | Semantic search — returns JSON array of `{content}` objects |
| **Document Parse** | Integration | `DocumentParser` bean (jaiclaw-documents) | PDF/HTML/text bytes → plain text; auto-detects base64 vs raw input |

---

## Adding your own processor

Implement `ConfigurableStageProcessor` + carry `@PipelineProcessor`
metadata — see [PIPELINE-STUDIO-PROCESSORS-SPI.md](../../dev/pipeline/PIPELINE-STUDIO-PROCESSORS-SPI.md).
Register the class as a Spring `@Bean`; the catalog service scans
for the annotation at boot and adds the entry to the palette.

## What's deferred

Per [PIPELINE-STUDIO-ANALYSIS.md](../../dev/pipeline/PIPELINE-STUDIO-ANALYSIS.md)
§6 "Explicitly not in baseline":

- **Memory Upsert** — `jaiclaw-memory` exposes read-only SPI today.
  Ships as a follow-up when the vector store gains an upsert method.
- **`switch` / `parallel`** — engine work first.
- **Batch-chunk** (Spring Batch stage) — greylisted.
- **Federation transports** — Phase 3 in PIPELINE-STRATEGY.md.
- **Channel-inbound stage nodes** — pipelines are triggered, not
  chat-driven.
