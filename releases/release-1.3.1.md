# JaiClaw 1.3.1 Release Notes

**Release Date:** 2026-10-26
**Distribution:** Maven Central + TapTech Nexus (`tooling.taptech.net`)

> 1.3.1 is a **dependency refresh.** No JaiClaw API, configuration key or
> runtime behaviour changes. It moves the framework onto Spring Boot 4.1.2,
> Apache Camel 4.23.0 LTS, Spock 2.5, and the current majors of fabric8, Drools
> and Testcontainers, and it fixes two long-standing nits in the launcher
> scripts. Spring AI stays at 2.0.1 on purpose — see below. Adopters who only consume the BOM get the Spring patches and
> Camel LTS by bumping one version; nothing else moves unless you import it.

## Highlights — dependency refresh

- **Spring Boot 4.1.1 → 4.1.2, Spring Cloud 2025.1.3 → 2025.1.4.** Boot 4.1.2
  moves managed Spring Framework to 7.0.10, Spring Security to 7.1.2, Jackson to
  3.1.7 and Netty to 4.2.19.
- **Spring AI stays at 2.0.1.** 2.0.2 (Anthropic Java SDK 2.72.0) was taken,
  verified and **reverted**: Embabel 1.5.3's Anthropic client fails with
  `401 authentication_error: invalid x-api-key` against an Anthropic-compatible
  proxy (MiniMax) under 2.0.2, while JaiClaw's own Spring AI path on 2.0.2 works
  (e2e scenario 3 passed). Reproduced with the pipeline example's LLM-backed
  Embabel stage: 2.0.1 completes in 9 s, 2.0.2 retries 401s until the stage
  times out. Anyone routing Embabel through a proxy would have been broken by
  the bump. Re-evaluate when Embabel ships a release built against Spring AI
  2.0.2 or later.
- **Apache Camel 4.21.0 → 4.23.0 (LTS).** `camel-dependencies` 4.23.0 pins the
  same Spring Boot 4.1 / Spring AI 2.0 line JaiClaw runs. Behaviour changes worth
  knowing if you configure Camel directly: `SSLContextParameters` now defaults to
  TLSv1.2 minimum; `@PropertyInject` fails on an invalid value instead of using
  the default; the Jasypt starter changed its default algorithm (re-encrypt
  existing values); `CAMEL_COMPONENT_*` environment variables now take effect.
  Kafka shared code moved to `camel-kafka-common` (same packages). The two
  pipeline modules that hardcoded `camel-core` 4.21.0 now follow
  `${camel.version}`, so they can no longer silently stay behind.
- **fabric8 kubernetes-client 7.1.0 → 8.0.0.** Java 17 baseline, Jackson 3
  (`tools.jackson.*`, which aligns it with Boot 4), and Vert.x 5.2.0 as the HTTP
  transport. `jaiclaw-tools-k8s` needed no code change. The version is now
  managed at the root through `io.fabric8:kubernetes-client-bom`, imported
  **before** the Spring Cloud BOM — Spring Cloud pins `kubernetes-client`
  7.4.1, and first-import-wins meant `jaiclaw-starter-k8s-monitor` (and any
  adopter importing both BOMs the same way) silently resolved 7.4.1 with the
  Vert.x 4.5 transport while the module itself compiled against 8.0.0. The
  release CVE scan caught it as CVE-2026-6860 on `vertx-core-4.5.25` in that one
  starter; the dead Vert.x 4.5 suppression was removed once the tree was
  uniformly 5.2.0.
- **Drools 9.44.0.Final → 10.2.0.** DRL and KIE APIs are unchanged; the rules
  suite passes as is. `drools-mvel` is deprecated upstream in favour of
  `drools-engine` and is still published; JaiClaw keeps it for this release.
- **Testcontainers 1.21.4 → 2.0.5, testcontainers-redis 2.2.4 → 3.0.0.**
  Test-scope only. Module artifacts are renamed (`postgresql` →
  `testcontainers-postgresql`) and container classes moved to
  `org.testcontainers.<module>`; JUnit 4 support is gone, which does not affect
  Spock.
- **GitHub Copilot SDK 1.0.5-01 → 1.0.23.** The SDK's generated records gain
  components in patch releases (`AssistantMessageEventData` went from 18 to 26),
  which broke only JaiClaw's spec fixtures. They now build records by component
  name; `CopilotChatModel` and `CopilotToolMapper` are unchanged.
- **Spock 2.4 → 2.5** (`groovy-5.0` classifier) and **Groovy 5.0.7 → 5.0.8**
  (the version Boot manages). Spock 2.5 throws on interactions declared against
  `null` and keeps interactions alive in `cleanup`; nothing in the suite tripped.
- Also: JJWT 0.12.6 → 0.13.0, objenesis 3.5 → 3.6, maven-surefire 3.6.0,
  maven-compiler 3.16.0, jacoco 0.8.15, JKube 1.20.0, Maven wrapper 3.9.16,
  `central-publishing-maven-plugin` 0.12.0 with `waitUntil=validated` (root and
  BOM), `maven-gpg-plugin` aligned to 3.2.8 in the BOM.

## Breaking changes

None in JaiClaw. If your own BOM overrides `camel.version`,
`io.fabric8:kubernetes-client`, Drools or Testcontainers, you inherit those
projects' majors and should read their migration notes (fabric8
`doc/MIGRATION-v8.md`; Testcontainers 2.0 artifact renames; KIE 10 migration
guide).

## Deferred to 1.4.0

Unchanged from 1.3.0: **`jaiclaw.security.default-tool-profile` still defaults
to `FULL`** and flips to `MINIMAL` in 1.4.0 — set it explicitly now; the Part E
Logto adapter; per-tool OAuth scopes; the A2A / checkpoint / session-primitive
scope in `docs/issues/IMPLEMENTATION-PLAN-1.3.0.md`. Spring Boot 4.2 / Spring
AI 2.1 / Spring Cloud 2026.0 wait for an Embabel release that targets Boot 4.2.

## Known limitations

- Everything listed for 1.3.0 still applies: the audit hash chain cannot detect
  tail truncation; SMS cannot verify Twilio signatures; approval requests are not
  persisted; the approval prompt shows all tool arguments; an approver without
  `user-id` is any participant; `CachingSecretsProvider` caches a tenant-scoped
  miss; prompt redaction is an SPI you must invoke.
- Two test suites are skipped by design and have been since before 1.3.0:
  `RedisTaskStoreContractSpec` (`@Ignore`; Spring Data Redis 4 MULTI/EXEC
  bookkeeping breaks the Jedis compare-and-set loop — the Redis task store is
  exercised by `RedisTaskStoreProviderSpec` instead) and the SEV-005b
  `@PendingFeature` in `MessagingMcpToolProviderSpec` (`broadcast_message`
  recipient tenant validation).
- The CVE scan for this release ran against the cached NVD database (horizon
  2026-08-24); there is still no NVD API key in the project. 0 unsuppressed
  findings across 11,851 dependency entries, with that caveat.
- `drools-mvel` is deprecated upstream; moving to `drools-engine` is a 1.4.0 item.

## Dependency updates

| Dependency | 1.3.0 | 1.3.1 |
|---|---|---|
| Spring Boot | 4.1.1 | **4.1.2** |
| Spring Framework (managed) | 7.0.9 | 7.0.10 |
| Spring AI | 2.0.1 | 2.0.1 (held — 2.0.2 breaks Embabel against Anthropic-compatible proxies) |
| Spring Cloud | 2025.1.3 | **2025.1.4** |
| Embabel Agent | 1.5.3 | 1.5.3 (latest) |
| Spring Shell | 4.0.3 | 4.0.3 (latest) |
| Apache Camel | 4.21.0 | **4.23.0 LTS** |
| Spock / Groovy | 2.4-groovy-5.0 / 5.0.7 | **2.5-groovy-5.0 / 5.0.8** |
| fabric8 kubernetes-client | 7.1.0 | **8.0.0** |
| Drools / KIE | 9.44.0.Final | **10.2.0** |
| Testcontainers / testcontainers-redis | 1.21.4 / 2.2.4 | **2.0.5 / 3.0.0** |
| GitHub Copilot SDK | 1.0.5-01 | **1.0.23** |
| JJWT | 0.12.6 | **0.13.0** |
| Playwright | 1.63.0 | 1.63.0 (latest) |
| objenesis | 3.5 | 3.6 |
| Jackson 3 / Netty (managed by Boot) | 3.1.5 / 4.2.17 | 3.1.7 / 4.2.19 |
| maven-surefire / maven-compiler / jacoco / JKube | 3.5.5 / 3.13.0 / 0.8.12 / 1.19.0 | 3.6.0 / 3.16.0 / 0.8.15 / 1.20.0 |
| central-publishing-maven-plugin | 0.9.0 (`waitUntil=published`) | 0.12.0 (`waitUntil=validated`) |
| Maven wrapper | 3.9.15 | 3.9.16 |

Deliberately not taken: Groovy 6 (arrow-`switch` becomes a statement, which
breaks Spock `expect:` idioms), OkHttp 5, LINE SDK 10, json-schema-validator 3,
jline 4, Stripe/PayPal SDKs, github-api 2.0-rc, and every Boot-managed version
override (Kotlin, Jackson, slf4j, Netty, H2, Jedis, json-path).

## Bug fixes

- **`jaiclaw-starter-k8s-monitor` resolved fabric8 7.4.1 instead of the 8.0.0
  the k8s tools module declared** — see the fabric8 highlight above. If your own
  POM imports `spring-cloud-dependencies`, import `kubernetes-client-bom` ahead
  of it or you inherit the same inversion.
- **`jaiclaw-example-pipeline-e2e` now registers `MiniMax-M2.7` as an Embabel
  model** (it ships `models/anthropic-models.yml`, as `camel-html-summarizer-embabel`
  always did) and lets `ANTHROPIC_MODEL` drive `embabel.models.default-llm`.
  Without it Embabel sent a Claude model name to MiniMax's Anthropic-compatible
  endpoint, which MiniMax maps to a default of its choosing — as of 2026-10-26
  that default (`MiniMax-M3.1-Flash`) rejects Embabel's structured-output
  request with a 400, so e2e scenario 6g failed with no code change on our side.
- **`quickstart.sh` and `start.sh` pinned jar paths and image tags to
  `0.1.0-SNAPSHOT`.** The "already built?" checks therefore never matched, so
  every `--local` launch rebuilt the whole reactor, and Docker mode asked
  `docker compose` for images that had never been built. Both scripts now read
  the version from `pom.xml` and export `JAICLAW_VERSION` for compose.
- `e2e/run-e2e-tests.sh` documented a default of scenarios 1–6 while running
  1,2,3,4,6; the header now says so (scenario 5 needs Docker).
- Scaffolder defaults (`jaiclaw-maven-plugin:scaffold`, the MCP scaffold tool
  and its README) referenced Spring Boot 4.1.0 and pre-1.0 JaiClaw versions;
  they now default to the released line.

## Security fixes

No CVE-driven changes. The dead Vert.x CVE-2026-6860 suppression was deleted
after fabric8 8 moved to Vert.x 5; the full-reactor scan reports 0 unsuppressed
findings (NVD cache horizon noted above).

## Documentation corrections

- `SECURITY.md` still said the `MINIMAL` default "becomes MINIMAL in 1.3.0" in
  one place; it is 1.4.0 everywhere now.
- `docs/dev/ARCHITECTURE.md` named Drools 9.44; the README stack line named
  Camel 4.21.0.
