# JaiClaw Pipeline E2E

Minimal Spring Boot app that exercises every UX improvement landed in the
`jaiclaw-pipeline` module so the e2e test runner can validate them end-to-end.

## Problem

The `jaiclaw-pipeline` module gained five UX improvements (startup validator,
`{{input}}`/`{{pipeline.*}}` templates, `.then()` DSL alias, execution tracker
backed by `/actuator/pipelines`, and `PipelineGateway` + `POST /api/pipelines/{id}/trigger`).
None of these were covered end-to-end — unit tests mock everything below the
route builder. A regression in the auto-config wiring or in the actuator
endpoint could land green on `mvn test` and still break consumers.

## Solution

This example is the minimum surface area needed to exercise all five features
against a real Spring Boot context:

- `processor-pipe` — two PROCESSOR stages chained with `.then(...)`, output
  template `"upper={{stages.upper.output}} input-was={{input}}"`. Needs no
  LLM key.
- `agent-pipe` — registered only when `JAICLAW_E2E_WITH_AGENT=true`, uses an
  AGENT stage that resolves `{{input}}`. Requires a provider API key.
- `application-broken.yml` — defines a pipeline with a missing bean and a
  typo'd stage reference. Used to confirm the validator fails startup with a
  consolidated, actionable message.

## Architecture

```
HTTP client
   │ POST /api/pipelines/processor-pipe/trigger  (body = "hello e2e")
   ▼
┌──────────────────────────┐
│ PipelineTriggerController│  Spring Web (Phase E)
└──────────────────────────┘
           │ 202 + handle
           ▼
┌──────────────────────────┐
│ DefaultPipelineGateway   │  asyncSend → direct:pipeline-processor-pipe
└──────────────────────────┘
           │
           ▼
   Camel route: stage "upper" → stage "exclaim" → output (LOG)
           │              (PROCESSOR)   (PROCESSOR)
           ▼
┌──────────────────────────┐
│ PipelineExecutionTracker │  records start / stages / SUCCESS  (Phase D)
└──────────────────────────┘
           │
           ▼
GET /actuator/pipelines/processor-pipe/{executionId}  →  status SUCCESS
```

Key classes:

| Class | Role |
|-------|------|
| `PipelineE2eApplication` | `@SpringBootApplication` entrypoint |
| `PipelineBeans` | Two `Function<String,String>` beans used as PROCESSOR stages |
| `E2ePipelines` | `JaiClawPipeline` subclass; defines `processor-pipe` and (optionally) `agent-pipe` |
| `application.yml` | Server port 8100, actuator exposes `pipelines`, no bundled skills |
| `application-broken.yml` | Validator-failure profile |

## Design

- **PROCESSOR-only by default.** The happy-path test must run in CI without
  any provider key. The AGENT stage is opt-in.
- **One binary, two modes.** A single jar covers both modes — environment
  flag flips the pipeline registration. Avoids a second example app.
- **YAML-driven broken profile.** The intentional defects live in a Spring
  profile YAML rather than in code so the validator failure can be triggered
  without recompiling. Makes the e2e clearer: same jar, two `--spring.profiles.active`
  invocations.
- **Tiny stage logic.** `upperCase`/`addExclaim` keep the validation focused
  on the pipeline plumbing, not on the bean behavior.

## Build & Run

```bash
# Prerequisites: Java 21, Maven settings that can resolve jaiclaw-* artifacts.
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# Build (will install jaiclaw-pipeline, jaiclaw-starter-pipeline if needed).
./mvnw package -pl :jaiclaw-example-pipeline-e2e -am -DskipTests -o
```

### Run the happy path

```bash
java -jar jaiclaw-examples/pipeline-e2e/target/jaiclaw-example-pipeline-e2e-*.jar \
    --server.port=8100
```

Drive the four checks the e2e runner uses. The HTTP trigger surface is
**alias-routed**: callers POST a trigger resource with a logical
`pipeline` field, the framework maps it through the
`jaiclaw.pipeline.http-trigger.allowed` map. Internal pipeline ids
never appear in the URL or response.

```bash
# 1. Alias-routed HTTP trigger — body shape: { pipeline, payload }
RESP=$(curl -sS -X POST http://localhost:8100/api/pipelines/trigger \
    -H 'Content-Type: application/json' \
    -d '{"pipeline":"processor-demo","payload":"hello e2e"}')
EXEC_ID=$(echo "$RESP" | jq -r .id)
echo "Submitted as $EXEC_ID"

# 2. 404 path — unknown alias rejected; pipeline ids never appear in the URL
curl -i -X POST http://localhost:8100/api/pipelines/trigger \
    -H 'Content-Type: application/json' \
    -d '{"pipeline":"does-not-exist","payload":"x"}'

# 3. Consumer-facing status — by execution id only, no pipeline id needed
sleep 1
curl -s "http://localhost:8100/api/pipelines/status/$EXEC_ID" | jq .

# 4. Operator-facing actuator (full record, pipeline id required)
curl -s http://localhost:8100/actuator/pipelines | jq .
curl -s "http://localhost:8100/actuator/pipelines/processor-pipe/$EXEC_ID" | jq .
```

Expected: step 1 returns 202 with `{"id":"...","submittedAt":"..."}` —
no `pipelineId` in the body. Step 2 returns 404 with
`{"error":"Unknown pipeline: does-not-exist"}`. Step 3 returns
`{"id":"...","status":"SUCCESS",...}` — no `pipelineId` / `tenantId`
leak. Step 4 (operator surface) still surfaces the full record.

### Trigger the validator failure

```bash
java -jar jaiclaw-examples/pipeline-e2e/target/jaiclaw-example-pipeline-e2e-*.jar \
    --spring.profiles.active=broken --server.port=0
```

The app must exit non-zero. The error message must include
`Pipeline 'broken-pipe' failed validation`, the `UNKNOWN_BEAN` code for
`notARealBean`, and a `did you mean 'research'?` suggestion for the
`resarch` typo.

### Optional: enable the AGENT pipeline

```bash
JAICLAW_E2E_WITH_AGENT=true \
ANTHROPIC_API_KEY=sk-ant-... \
    java -jar jaiclaw-examples/pipeline-e2e/target/jaiclaw-example-pipeline-e2e-*.jar \
    --server.port=8100

curl -X POST http://localhost:8100/api/pipelines/trigger \
    -H 'Content-Type: application/json' \
    -d '{"pipeline":"weather-agent","payload":"how is the weather?"}'
```

### Optional: enable the EMBABEL pipeline (runtime=EMBABEL)

`embabel-pipe` exercises the `runtime: embabel` AGENT-stage path that
shipped in 0.9.1. It uses a pure-compute Embabel `@Agent`
(`TicketScoringAgent`) — no LLM key required for the default flow,
which makes this safe to run in CI.

```bash
JAICLAW_E2E_WITH_EMBABEL=true \
    java -jar jaiclaw-examples/pipeline-e2e/target/jaiclaw-example-pipeline-e2e-*.jar \
    --server.port=8100

# Alias-routed: caller never sees that this maps to embabel-pipe.
ID=$(curl -sS -X POST http://localhost:8100/api/pipelines/trigger \
     -H 'Content-Type: application/json' \
     -d '{"pipeline":"ticket-scoring","payload":"priority:high size:large"}' \
     | jq -r .id)

# Poll consumer-facing status by id (no pipeline id in the URL).
curl -sS "http://localhost:8100/api/pipelines/status/$ID" | jq .
```

Expected app-log markers (in order, all INFO level):

```
INFO ... Pipeline stage start — stage=score runtime=EMBABEL workflow=TicketScoringAgent timeout=...
INFO ... Embabel orchestration port — execute start workflow=TicketScoringAgent input-keys=[it] input-size~=27
INFO ... Embabel agent lookup — workflow=TicketScoringAgent resolved=true
INFO ... Embabel run — workflow=TicketScoringAgent input-keys=[it]
INFO ... TicketScoringAgent.parse — input='priority:high size:large'
INFO ... TicketScoringAgent.parse — parsed=ParsedTicket[priority=high, size=large]
INFO ... TicketScoringAgent.score — ticket=ParsedTicket[priority=high, size=large]
INFO ... TicketScoringAgent.score — result=ScoreResult[..., score=100, ...]
INFO ... Embabel result extracted — type=ScoreResult length=N
INFO ... Embabel orchestration port — execute end workflow=TicketScoringAgent success=true duration=...ms
INFO ... Pipeline stage — stage=score runtime=EMBABEL workflow=TicketScoringAgent success=true ...
```

When `ANTHROPIC_API_KEY` is ALSO set, a second pipeline
`embabel-triage-pipe` is registered — it routes through the LLM-backed
`TicketTriageAgent`:

```bash
JAICLAW_E2E_WITH_EMBABEL=true \
ANTHROPIC_API_KEY=sk-ant-... \
    java -jar jaiclaw-examples/pipeline-e2e/target/jaiclaw-example-pipeline-e2e-*.jar \
    --server.port=8100

curl -X POST http://localhost:8100/api/pipelines/trigger \
    -H 'Content-Type: application/json' \
    -d '{"pipeline":"ticket-triage","payload":"Users report login button does not respond after the latest release"}'
```

### Loading pipelines from a YAML file

This example also ships a pipeline definition as a standalone YAML resource
at `src/main/resources/jaiclaw/pipelines/processor-pipe-from-file.yml`.
`application.yml` opts into per-file loading via:

```yaml
jaiclaw:
  pipeline:
    locations:
      patterns:
        - "classpath*:jaiclaw/pipelines/*.yml"
```

On boot, you'll see `Registered file pipeline: 'processor-pipe-from-file'`
in the log alongside `Registered code pipeline: 'processor-pipe'`. The same
HTTP / actuator endpoints work for both:

```bash
curl -X POST http://localhost:8100/api/pipelines/processor-pipe-from-file/trigger \
    -H 'Content-Type: text/plain' -d 'hello from file'
```

To load YAML pipelines from outside the jar, add a filesystem pattern to
`locations.patterns`, e.g.:

```yaml
jaiclaw:
  pipeline:
    locations:
      patterns:
        - "classpath*:jaiclaw/pipelines/*.yml"
        - "file:${HOME}/.jaiclaw/pipelines/*.yml"
```

Each file contains exactly one `PipelineDefinition` at top level. If you omit
`id:`, the loader uses the filename stem (`my-pipeline.yml` →
`my-pipeline`). Source precedence (later overrides earlier on id conflict):
inline `pipelines[]` → per-file YAML → `JaiClawPipeline` code beans.

## How the e2e runner uses this app

`e2e/run-e2e-tests.sh`'s `run_scenario_6` builds this module, runs the broken
profile expecting a non-zero exit (sub-result `6a-Validator`), starts the
happy path on `E2E_PIPELINE_PORT` (default 8100), and drives the four
curl checks above. Sub-results: `6b-HTTP-trigger`, `6c-Actuator`,
`6d-Template`. The optional AGENT path runs only when both
`JAICLAW_E2E_WITH_AGENT=true` and a provider key are present.
