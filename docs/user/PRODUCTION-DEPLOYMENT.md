# Production Deployment Guide

This guide is for operators putting JaiClaw 0.8.0 into a real production
environment. It covers container images, Kubernetes manifests, a minimal
Helm chart, secrets, observability via Spring Boot Actuator + Micrometer,
health probes, rolling upgrades, and a short operational runbook.

If you are still evaluating JaiClaw or running it locally, start with
[OPERATIONS.md](./OPERATIONS.md) — that document covers the daily-driver
workflow (`start.sh`, `bin/jaiclaw`, local provider setup). This guide
assumes you have a working configuration and want to ship it.

> JaiClaw 0.8.0 is pre-1.0. The API/SPI surface is governed by
> `@Stable` / `@Experimental` / `@Internal` markers
> ([P3.5](../CODEBASE-ANALYSIS-2026-06-10.md)). Deploying with
> `@Experimental` features in production is supported — just track them
> against the [MIGRATION-0.8.md](../MIGRATION-0.8.md) and forthcoming
> per-release upgrade guides.

---

## Contents

1. [Architecture targets](#1-architecture-targets)
2. [Building the container images](#2-building-the-container-images)
3. [Secrets and configuration](#3-secrets-and-configuration)
4. [Kubernetes manifests](#4-kubernetes-manifests)
5. [Helm chart](#5-helm-chart)
6. [Observability (Actuator + Micrometer)](#6-observability-actuator--micrometer)
7. [Health probes and rolling upgrades](#7-health-probes-and-rolling-upgrades)
8. [Resource sizing](#8-resource-sizing)
9. [Security hardening](#9-security-hardening)
10. [Cloud provider notes](#10-cloud-provider-notes)
11. [Runbook](#11-runbook)

---

## 1. Architecture targets

A production JaiClaw deployment usually has two workloads:

| Workload                | Image                     | Replicas | Notes |
|-------------------------|---------------------------|----------|-------|
| **Gateway**             | `io.jaiclaw/jaiclaw-gateway-app` | 2–N | Stateless HTTP + WebSocket; horizontally scalable behind a `Service` |
| **Shell** (optional)    | `io.jaiclaw/jaiclaw-shell`       | 1 (Deployment with replicas: 0; scale to 1 for an interactive session) | Operator/admin shell; not exposed to traffic |

Both run on `eclipse-temurin:21-jre`. The gateway is what end-users hit
through whichever channel adapter you enabled (Telegram webhook, Slack
Events, Discord Gateway, IMAP/SMTP, Twilio SMS, web UI, …).

For multi-tenant deployments, run **one gateway per agent** if tenants
should not share an LLM key budget, **one gateway across all tenants** if
they should. Both topologies work with the same image — only the config
mount differs.

---

## 2. Building the container images

JaiClaw uses [JKube](https://eclipse.dev/jkube/) for container builds.
Two profiles are available:

```bash
export JAVA_HOME=/path/to/java-21

# Standard multi-arch build (linux/amd64 + linux/arm64 if BuildKit is set up)
./mvnw package k8s:build \
  -pl jaiclaw-gateway-app,jaiclaw-shell \
  -am -Pk8s -DskipTests

# Inline-assembly build (linux/amd64 only, faster for CI)
./mvnw k8s:build \
  -pl jaiclaw-gateway-app \
  -Pk8s-inline -DskipTests

# Push to your registry
./mvnw k8s:push -pl jaiclaw-gateway-app,jaiclaw-shell -Pk8s
```

By default JKube tags images as `io.jaiclaw/jaiclaw-gateway-app:<version>`.
Override the registry by setting `-Djkube.docker.imagePullPolicy.repository`
or by editing `jkube.docker.registry` in the module POMs.

> **macOS Helm note:** if you also use Helm to deploy from macOS, prefer
> the Docker-based deploy approach (`helm/helm-deploy.sh`) over running
> `helm` directly. macOS extended attributes (`com.apple.provenance`) on
> chart files cause `Chart.yaml is missing` errors with the native binary.

---

## 3. Secrets and configuration

### 3.1 Secrets

Create a single Kubernetes secret that holds every credential the gateway
needs. Channel-related entries are only required if you enabled that
channel.

```bash
kubectl create namespace jaiclaw

kubectl -n jaiclaw create secret generic jaiclaw-secrets \
  --from-literal=JAICLAW_API_KEY=$(openssl rand -hex 32) \
  --from-literal=ANTHROPIC_API_KEY=sk-ant-... \
  --from-literal=OPENAI_API_KEY=sk-... \
  --from-literal=TELEGRAM_BOT_TOKEN=123456:ABC... \
  --from-literal=SLACK_BOT_TOKEN=xoxb-... \
  --from-literal=SLACK_SIGNING_SECRET=... \
  --from-literal=TWILIO_ACCOUNT_SID=AC... \
  --from-literal=TWILIO_AUTH_TOKEN=... \
  --from-literal=EMAIL_USERNAME=bot@example.com \
  --from-literal=EMAIL_PASSWORD=...
```

> **0.8.0 hard-break note:** the legacy `jclaw_ak_…` API-key prefix was
> dropped in 0.8.0. New keys should be plain bytes (e.g. `openssl rand
> -hex 32`); the gateway uses constant-time comparison (`jaiclaw.security.
> timing-safe-api-key` defaults to **on** in 0.8.0). See
> [MIGRATION-0.8.md](../MIGRATION-0.8.md) §P3.5.

### 3.2 Application config

The gateway looks for `application.yml` in `/config/` on the container.
Mount your environment-specific overrides as a `ConfigMap`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: jaiclaw-config
  namespace: jaiclaw
data:
  application.yml: |
    spring:
      profiles:
        active: production,security-hardened

    server:
      port: 8888
      shutdown: graceful

    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus,pipelines
      endpoint:
        health:
          probes:
            enabled: true
          group:
            readiness:
              include: readinessState,db,redis
      metrics:
        tags:
          application: jaiclaw-gateway
          environment: production

    jaiclaw:
      tenant:
        mode: single                # or 'multi' — see §9
        default-tenant-id: ${JAICLAW_DEFAULT_TENANT_ID:default}

      skills:
        allow-bundled: []           # whitelist explicitly; see CLAUDE.md

      identity:
        name: "Production Agent"

      models:
        minimax:
          filter-thinking: true
```

**Do not** leave `jaiclaw.skills.allow-bundled` unset in production — the
default `["*"]` loads ~26k tokens of irrelevant skill instructions into
every LLM request. See [CLAUDE.md](../../CLAUDE.md) § Skills.

---

## 4. Kubernetes manifests

A minimal Deployment + Service + Ingress for the gateway:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaiclaw-gateway
  namespace: jaiclaw
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: jaiclaw-gateway
  template:
    metadata:
      labels:
        app: jaiclaw-gateway
    spec:
      containers:
        - name: gateway
          image: registry.example.com/io.jaiclaw/jaiclaw-gateway-app:0.8.0
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8888
          envFrom:
            - secretRef:
                name: jaiclaw-secrets
          env:
            - name: SPRING_CONFIG_ADDITIONAL_LOCATION
              value: file:/config/
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
          resources:
            requests:
              cpu: "1000m"
              memory: "1Gi"
            limits:
              cpu: "2000m"
              memory: "2Gi"
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            failureThreshold: 30
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            periodSeconds: 5
          volumeMounts:
            - name: config
              mountPath: /config
              readOnly: true
            - name: workspace
              mountPath: /workspace
      volumes:
        - name: config
          configMap:
            name: jaiclaw-config
        - name: workspace
          emptyDir: {}        # swap for a PVC if your agents need persistent file state
---
apiVersion: v1
kind: Service
metadata:
  name: jaiclaw-gateway
  namespace: jaiclaw
spec:
  selector:
    app: jaiclaw-gateway
  ports:
    - name: http
      port: 80
      targetPort: http
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: jaiclaw-gateway
  namespace: jaiclaw
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  ingressClassName: nginx
  tls:
    - hosts: [agent.example.com]
      secretName: jaiclaw-gateway-tls
  rules:
    - host: agent.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: jaiclaw-gateway
                port:
                  number: 80
```

The startup-probe budget — 30 failures × 5 s = 150 s — is intentionally
generous. Spring Boot 3.5 + Spring AI + every JaiClaw auto-config takes
20–60 seconds to start cold on 1 vCPU. Tightening the probe before you've
measured your actual cold-start with `kubectl logs --previous` will cause
spurious restart loops.

---

## 5. Helm chart

JaiClaw doesn't ship its own Helm chart yet, but the gateway is a vanilla
Spring Boot app that drops straight into any general-purpose
"spring-boot-app" chart. A minimal `values.yaml` for such a chart:

```yaml
image:
  repository: registry.example.com/io.jaiclaw/jaiclaw-gateway-app
  tag: "0.8.0"

replicaCount: 2

service:
  type: ClusterIP
  port: 80
  targetPort: 8888

probes:
  startup:
    path: /actuator/health/liveness
    failureThreshold: 30
    periodSeconds: 5
  liveness:
    path: /actuator/health/liveness
  readiness:
    path: /actuator/health/readiness

env:
  SPRING_PROFILES_ACTIVE: production,security-hardened
  SPRING_CONFIG_ADDITIONAL_LOCATION: file:/config/

envFromSecret: jaiclaw-secrets

configMap:
  name: jaiclaw-config
  mountPath: /config

resources:
  requests: { cpu: 1000m, memory: 1Gi }
  limits:   { cpu: 2000m, memory: 2Gi }

properties:
  spring.profiles.active: "production,security-hardened"

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: agent.example.com
      paths: [{ path: /, pathType: Prefix }]
  tls:
    - hosts: [agent.example.com]
      secretName: jaiclaw-gateway-tls
```

If your chart sets `-Dspring.profiles.active=kubernetes` via the
`properties` block (as `spring-boot-app` does), make sure to override it
explicitly — that JVM property silently beats the `SPRING_PROFILES_ACTIVE`
env var (see [memory note](../../CLAUDE.md#helm-chart-default-properties)).

---

## 6. Observability (Actuator + Micrometer)

The `jaiclaw-observability` extension auto-configures a Micrometer-backed
metrics surface. Add the dependency to your gateway POM (it's not in the
default starter):

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-observability</artifactId>
</dependency>
```

This wires four counters + one timer onto your existing
`MeterRegistry`:

| Metric                          | Type    | Tags                                     | Meaning |
|---------------------------------|---------|------------------------------------------|---------|
| `jaiclaw.agent.invocations`     | Counter | `agentId`, `channelId`                   | One per agent turn |
| `jaiclaw.tool.calls`            | Timer   | `toolName`, `outcome`                    | Tool dispatch latency |
| `jaiclaw.channel.messages`      | Counter | `channelId`, `direction` (in / out)      | Per-channel message volume |
| `jaiclaw.tokens.usage`          | Counter | `model`, `tokenType` (input / output)    | Token spend |
| `jaiclaw.sessions.active`       | Gauge   | —                                        | Live session count |

### 6.1 Prometheus scrape

If you add `micrometer-registry-prometheus` to the gateway:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

`/actuator/prometheus` becomes available. Scrape it with:

```yaml
- job_name: jaiclaw-gateway
  kubernetes_sd_configs:
    - role: pod
      namespaces: { names: [jaiclaw] }
  relabel_configs:
    - source_labels: [__meta_kubernetes_pod_label_app]
      action: keep
      regex: jaiclaw-gateway
  metrics_path: /actuator/prometheus
```

### 6.2 Pipeline observability

The `jaiclaw-pipeline` extension exposes its own actuator endpoint at
`/actuator/pipelines`:

- `GET /actuator/pipelines` — list every registered pipeline
- `GET /actuator/pipelines/{id}` — definition + last N executions
- `GET /actuator/pipelines/{id}/{executionId}` — full execution detail

The execution tracker also writes structured logs that aggregators (Loki,
ELK, Datadog) can parse for cross-pipeline correlation.

### 6.3 Suggested dashboards

A reasonable starting Grafana dashboard tracks:

1. `rate(jaiclaw_agent_invocations_total[5m])` — agent throughput
2. `histogram_quantile(0.95, sum(rate(jaiclaw_tool_calls_seconds_bucket[5m])) by (le, toolName))` — p95 tool latency
3. `sum by (model, tokenType) (rate(jaiclaw_tokens_usage_total[1h]))` — hourly token spend
4. `jaiclaw_sessions_active` — concurrent sessions

---

## 7. Health probes and rolling upgrades

Spring Boot 3.5 exposes three liveness/readiness states out of the box:

- `/actuator/health/liveness` — JVM is alive
- `/actuator/health/readiness` — app is ready to serve traffic (depends
  on `db`, `redis`, etc. if you've grouped them)
- `/actuator/health` — overall

Set the readiness group in `application.yml` to include any external
backend you depend on (Postgres, Redis, vector store). When that backend
flaps, readiness goes red, the Service drops the pod from rotation, but
the pod isn't killed — Spring will recover automatically.

For rolling upgrades:

- `maxUnavailable: 0` + `maxSurge: 1` keeps capacity stable
- `terminationGracePeriodSeconds: 60` gives in-flight LLM streams time to
  finish; the gateway honors `server.shutdown: graceful`
- Inbound webhooks (Telegram, Slack, Twilio) cope with brief 502s on
  rotation; Discord's Gateway WebSocket auto-reconnects with backoff

If you run **stateful** channels (Discord's Gateway WebSocket, IMAP
polling), a `RollingUpdate` is still safe — only one connection is broken
at a time and the new pod re-establishes it on startup. If you've
configured Telegram via long-polling (not webhook), prefer
`replicas: 1` to avoid duplicate message processing.

---

## 8. Resource sizing

Starting points, tuned later from actual metrics:

| Workload size              | CPU req / lim | Memory req / lim | JVM `-Xmx` |
|----------------------------|---------------|------------------|------------|
| Personal / demo            | 250m / 500m   | 512Mi / 1Gi      | 512m       |
| Small team (< 50 RPS)      | 1000m / 2000m | 1Gi / 2Gi        | 1500m      |
| Production (100+ RPS)      | 2000m / 4000m | 2Gi / 4Gi        | 3000m      |
| Heavy GOAP planning        | +1000m CPU per concurrent session | +512Mi per session | +512m |

`-XX:MaxRAMPercentage=75` lets the JVM grow to ~75% of the container
limit; pair it with `-XX:+ExitOnOutOfMemoryError` so Kubernetes restarts
on OOM rather than thrashing.

> **K8s ≥1000m for reasonable startup.** 250m on Spring Boot 3.5 causes
> startup probe timeouts. Memory: this is a [memory rule from prior
> production deploys](../../CLAUDE.md#deploy-best-practices).

---

## 9. Security hardening

JaiClaw 0.8.0 ships a `security-hardened` Spring profile that flips all
hardening switches on:

```yaml
spring:
  profiles:
    active: security-hardened
```

Activated by that profile:

| Flag                                      | Default off → on | Effect |
|-------------------------------------------|------------------|--------|
| `jaiclaw.channels.slack.verify-signature` | ✓ | Slack webhook HMAC-SHA256 verification |
| `jaiclaw.channels.telegram.verify-webhook`| ✓ | Telegram secret-token verification |
| `jaiclaw.channels.telegram.mask-bot-token`| ✓ | SHA-256 hash in session keys (avoids token leak in logs) |
| `jaiclaw.tools.web.ssrf-protection`       | ✓ | Block private/internal IPs in WebFetchTool |
| `jaiclaw.tools.code.workspace-boundary`   | ✓ | Path-traversal protection in code tools |
| `jaiclaw.security.timing-safe-api-key`    | ✓ | Constant-time API key comparison |

For multi-tenant deployments **also** enable:

```yaml
jaiclaw:
  tenant:
    mode: multi
    default-tenant-id: ${JAICLAW_DEFAULT_TENANT_ID}   # high-entropy, not "default"
    strict-default-tenant-id: true                    # rejects weak default ids on startup
    config-locations:
      - file:/config/tenants/
    tenant-header: X-Tenant-Id
```

`strict-default-tenant-id: true` rejects low-entropy ids
(`default`, length < 16, lowercase-only) at startup. Generate a UUID and
mount it in the secret:

```bash
kubectl -n jaiclaw patch secret jaiclaw-secrets --type merge \
  -p "{\"stringData\":{\"JAICLAW_DEFAULT_TENANT_ID\":\"$(uuidgen)\"}}"
```

See [MIGRATION-0.8.md](../MIGRATION-0.8.md) for the rationale and the
in-memory store hardening that went with it.

---

## 10. Cloud provider notes

### AWS (EKS)

- Use IAM Roles for Service Accounts (IRSA) for any AWS SDK access from
  tools (S3, Bedrock); never embed AWS access keys in the secret.
- The gateway works behind an ALB Ingress; set
  `alb.ingress.kubernetes.io/healthcheck-path: /actuator/health/liveness`.
- Bedrock as an LLM provider: add `spring-ai-starter-model-bedrock` and
  configure `spring.ai.bedrock.aws.*`. The pod's IRSA role needs
  `bedrock:InvokeModel` and `bedrock:InvokeModelWithResponseStream`.

### GCP (GKE)

- Use Workload Identity for Vertex AI access.
- Vertex as an LLM provider: add `spring-ai-starter-model-vertex-ai` and
  set `spring.ai.vertex.ai.project-id`.

### Azure (AKS)

- Use Workload Identity for Azure OpenAI.
- Azure OpenAI: add `spring-ai-starter-model-azure-openai`; set
  `spring.ai.azure.openai.endpoint` and use a managed-identity-derived
  token rather than a static key.

### Self-hosted / on-prem

- If you front the gateway with HAProxy or Caddy, terminate TLS there
  and keep the gateway on `http://`.
- For Ollama-backed inference, run the Ollama pods in the same cluster
  and set `spring.ai.ollama.base-url=http://ollama.ollama.svc:11434`.

---

## 11. Runbook

### 11.1 Pod is crash-looping right after a new tag

1. `kubectl logs -n jaiclaw <pod> --previous` — look for
   `IllegalArgumentException: Default LLM 'X' not found in available
   models`. That's the three-layer Embabel/Spring AI config foot-gun
   ([memory note](../../CLAUDE.md#spring-ai-auto-configuration-ordering-critical)).
2. Confirm `embabel.models.default-llm` matches a model name registered
   by the embabel provider starter you've included — not the API model
   name. They are not the same value.

### 11.2 Startup probe is failing on cold start

- Bump `startupProbe.failureThreshold` (CPU limits, Spring AI auto-config
  count, plugin count all push cold-start time).
- Confirm you have CPU **requests** at 1000m+; 250m is the most common
  cause.

### 11.3 Tenants seeing each other's data

Multi-tenant isolation gaps were addressed in 0.8.0
([MIGRATION-0.8.md](../MIGRATION-0.8.md) Multi-Tenant Isolation Hardening).
If you see cross-tenant bleed:

1. Confirm you're on 0.8.0+ — earlier versions had unscoped in-memory
   stores.
2. Confirm `jaiclaw.tenant.default-tenant-id` is **not** the literal
   string `default`. The startup WARN log fires once on each boot if it
   is — search for "still the literal string 'default'".
3. For voice-call deployments, check `JsonlCallStore`'s on-disk layout
   — the file should live at `{base}/{tenantId}/calls.jsonl`, not at
   `{base}/calls.jsonl`. The startup migration moves legacy files
   automatically.

### 11.4 Token spend is higher than expected

1. Check `jaiclaw.skills.allow-bundled` — if unset, every request loads
   ~26k tokens of bundled skills. Setting it to `[]` cuts that to zero.
2. Hit `/actuator/metrics/jaiclaw.tokens.usage` and confirm the rate
   matches your dashboard.
3. Confirm the Embabel default-llm isn't routing to a more expensive
   model than you intended via Spring AI's
   `spring.ai.anthropic.chat.options.model` override.

### 11.5 Pipelines are failing on startup with "Unknown stage ref"

The `PipelineValidator` runs at `Ordered.HIGHEST_PRECEDENCE` and refuses
to start on broken refs. The log message includes a "did you mean?"
suggestion (Levenshtein ≤ 2) — fix the typo and redeploy. To
intentionally bypass the validator (not recommended), unset
`jaiclaw.pipeline.enabled`.

### 11.6 LLM provider 429 / rate-limit storms

- Increase `jaiclaw.tool-loop.max-iterations` cautiously — a runaway
  tool loop multiplies your LLM bill.
- For Anthropic, the OAuth-backed providers (Chutes, Codex,
  Gemini CLI) have per-account rate limits; switch with
  `./start.sh login <provider>` and roll the pod.

---

## See also

- [OPERATIONS.md](./OPERATIONS.md) — local dev / daily-driver workflows
- [CONFIGURATION.md](./CONFIGURATION.md) — every `jaiclaw.*` property
- [MIGRATION-0.8.md](../MIGRATION-0.8.md) — 0.7.x → 0.8.0 upgrade guide
- [ARCHITECTURE.md](../dev/ARCHITECTURE.md) — module / dependency view
- [multi-tenancy-architecture.md](../dev/multi-tenancy-architecture.md) —
  tenant resolution + storage isolation
