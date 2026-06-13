# Docker Layer Caching Options for JKube Builds in GitHub Actions

How to stop `kubernetes-maven-plugin` (Eclipse JKube) from cold-building Docker
images on every GitHub Actions run, and how to pick between the three viable
caching strategies.

## The problem

JKube's default `docker` build strategy talks to the Docker daemon API. The
daemon's layer cache lives on the runner — and GitHub-hosted runners are
ephemeral:

```
  Run #1 (runner-abc)
┌────────────────────────────┐
│                            │                   runner +
│  k8s:build  -> COLD build  │                   cache
│                            │ --- job ends ---> DESTROYED
│  daemon layer cache: warm  │
│                            │                      X
└────────────────────────────┘

  Run #2 (runner-xyz, fresh)
┌────────────────────────────┐
│                            │
│  k8s:build  -> COLD again  │
│                            │ <-- nothing survives between runs
│  daemon layer cache: empty │
│                            │
└────────────────────────────┘
```

The fix in every case is to move the layer cache somewhere that outlives the
runner. Where it goes is the decision:

```
         ┌──────────────────────────────────────┐
         │      Where should the BuildKit       │
         │          layer cache live?           │
         └──────────────────────────────────────┘
                            |
           +----------------+----------------+
           |                |                |
           v                v                v
┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐
│     OPTION A       │  │    OPTION B *      │  │     OPTION C       │
│                    │  │                    │  │                    │
│  GHA cache svc     │  │  Registry (GHCR)   │  │  Jib local dir     │
│  type=gha          │  │  type=registry     │  │  + actions/cache   │
│  10 GB limit       │  │  :buildcache ref   │  │  (no daemon)       │
└────────────────────┘  └────────────────────┘  └────────────────────┘

* recommended for Dockerfile-based multi-image builds
```

Options A and B both require switching JKube to **buildx mode** by adding a
`<buildx>` block to the image build config. JKube then shells out to
`docker buildx build`, unlocking BuildKit's `--cache-from` / `--cache-to`
exporters. Option C replaces the daemon entirely with Jib.

## Option A — GitHub Actions cache service (`type=gha`)

BuildKit exports the layer cache to GitHub's own cache service.

```xml
<buildx>
  <platforms><platform>linux/amd64</platform></platforms>
  <cacheFrom>type=gha,scope=myapp</cacheFrom>
  <cacheTo>type=gha,scope=myapp,mode=max</cacheTo>
</buildx>
```

```
┌─[ OPTION A FINE PRINT ]────────────────────────────────────────┐
│type=gha authenticates via ACTIONS_RUNTIME_TOKEN env vars that  │
│only docker/build-push-action exposes automatically. JKube      │
│invokes buildx itself, so you must add                          │
│crazy-max/ghaction-github-runtime to expose them. Also: only    │
│GitHub Cache API v2 works since April 2025 -- old               │
│buildx/BuildKit versions fail outright.                         │
└────────────────────────────────────────────────────────────────┘
```

Further constraints:

- 10 GB per-repo cache with LRU eviction — fat Java layers (Oracle base
  images, Spring Boot fat jars) churn it quickly.
- Default `scope` is shared; multiple images in one reactor build overwrite
  each other's cache unless each gets a distinct `scope=<image>`.
- Cache access rules apply: only the current branch, base branch, and default
  branch caches are readable.

**Choose when:** single small image, no desire to write cache artifacts to a
registry.

## Option B — Registry cache (`type=registry`, GHCR) — recommended

BuildKit imports/exports the cache as an OCI artifact living next to your
images. No token plumbing, no 10 GB ceiling, identical behavior from any CI
system.

```xml
<buildx>
  <platforms><platform>linux/amd64</platform></platforms>
  <cacheFrom>type=registry,ref=ghcr.io/your-org/your-app:buildcache</cacheFrom>
  <cacheTo>type=registry,ref=ghcr.io/your-org/your-app:buildcache,mode=max</cacheTo>
</buildx>
```

Workflow needs only `setup-buildx-action` + a normal `docker login` to GHCR
before the Maven invocation.

**Choose when:** Dockerfile-based builds, multi-image reactors, large layers.
Full implementation guide: see
`jkube-registry-cache-github-actions.md` (companion document).

## Option C — JIB strategy + `actions/cache`

Switch strategies entirely: `-Djkube.build.strategy=jib`. Jib builds without a
Docker daemon and keeps base-image and application layers in an on-disk cache
that plain `actions/cache` can persist:

```yaml
- uses: actions/cache@v4
  with:
    path: ~/.cache/google-cloud-tools-java/jib
    key: jib-${{ runner.os }}-${{ hashFiles('**/pom.xml') }}
    restore-keys: jib-${{ runner.os }}-
```

Jib layers dependencies / resources / classes separately, so unchanged
dependency layers are never re-pushed — extremely effective for Spring Boot.

**Choose when:** your images need no custom Dockerfile steps. If the
Dockerfile installs packages, tooling, or otherwise does real work, Jib cannot
replicate it — use Option A/B.

## Comparison

| | A: `type=gha` | B: `type=registry` | C: Jib + `actions/cache` |
|---|---|---|---|
| JKube mode | buildx | buildx | jib strategy |
| Size limit | 10 GB/repo, LRU | registry quota | 10 GB/repo (actions/cache) |
| Extra workflow steps | buildx setup + runtime-token helper | buildx setup + registry login | cache step only |
| Custom Dockerfile support | yes | yes | no |
| Multi-image reactor | needs per-image `scope` | per-image `:buildcache` ref | natural |
| Registry side-effects | none | writes `:buildcache` artifact | none |
| Fragility | cache API version churn, token plumbing | low | low |

## Regardless of option: cache Maven dependencies

For Spring Boot builds the dependency download often dominates wall-clock time
over image layers. This stacks with all three options:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '21'
    cache: maven        # persists ~/.m2/repository
```

## Recommendation

Registry cache (**Option B**) for Dockerfile-based images, paired with the
Maven dependency cache. It is the least fragile combination: no runtime-token
plumbing, no cache-service API churn, no 10 GB ceiling, and the cache artifact
lives next to the images in GHCR where it can be inspected or deleted freely.
