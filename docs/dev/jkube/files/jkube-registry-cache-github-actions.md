# Docker Layer Caching for JKube Builds in GitHub Actions (Registry Cache)

Implementation guide for BuildKit layer caching with the Eclipse JKube
`kubernetes-maven-plugin` in GitHub Actions, using a **registry-backed cache**
(GHCR). For the comparison against `type=gha` and Jib, see the companion
document `jkube-caching-options-overview.md`.

## Architecture

```
  GitHub Actions runner (ephemeral)            GHCR (ghcr.io)
┌──────────────────────────────────┐         ┌─────────────────────────────┐
│                                  │         │                             │
│  mvn k8s:build k8s:push          │push imgs│  app:1.2.3      (image)     │
│          |                       │--------->  app:latest     (image)     │
│          v                       │         │                             │
│  JKube kubernetes-maven-plugin   │         │                             │
│          |                       │         │  app:buildcache (cache      │
│          v                       │<---------                  manifest)  │
│  docker buildx build             │cacheFrom│                             │
│  (docker-container driver)       │         │                             │
│                                  │--------->                             │
│                                  │ cacheTo │                             │
└──────────────────────────────────┘         └─────────────────────────────┘

cacheFrom = import layers (type=registry)  cacheTo = export layers (mode=max)
```

How it works:

1. The `<buildx>` block makes JKube invoke `docker buildx build` instead of
   the daemon's legacy builder.
2. `cacheFrom` **imports** a cache manifest from `…:buildcache` before the
   build; unchanged layers are reused instead of rebuilt.
3. `cacheTo` with `mode=max` **exports** all intermediate layers (not just
   final image layers) back to the same ref — this is what makes multi-stage
   Dockerfiles cache effectively.
4. The `:buildcache` ref is just an OCI artifact. Deleting it never breaks a
   build; the next run is simply cold and re-exports.

## Prerequisites

```
╭─[ CHECK FIRST: JKube version ]─────────────────────────────────╮
│Verify your pinned JKube version supports <cacheFrom>/<cacheTo> │
│inside <buildx> (1.14+). Run `mvn k8s:build -X` and confirm     │
│--cache-from / --cache-to appear on the logged buildx command   │
│line before trusting the cache is active.                       │
╰────────────────────────────────────────────────────────────────╯
```

```
╔═[ GOTCHA: cache export needs the right driver ]════════════════╗
║The default "docker" buildx driver CANNOT export caches. You    ║
║must create a docker-container driver builder                   ║
║(docker/setup-buildx-action does this) or cacheTo will fail     ║
║with: "Cache export is not supported for the docker driver".    ║
╚════════════════════════════════════════════════════════════════╝
```

Also required: a registry the workflow can push to. This guide uses GHCR
authenticated with the built-in `GITHUB_TOKEN` (workflow needs
`packages: write`).

## Maven configuration

### Image configuration with buildx cache

```xml
<plugin>
  <groupId>org.eclipse.jkube</groupId>
  <artifactId>kubernetes-maven-plugin</artifactId>
  <version>${jkube.version}</version>
  <configuration>
    <images>
      <image>
        <name>ghcr.io/your-org/your-app:%l</name>
        <build>
          <dockerFile>${project.basedir}/Dockerfile</dockerFile>
          <buildx>
            <platforms>
              <platform>linux/amd64</platform>
            </platforms>
            <!-- Resolved from properties so local builds can disable caching -->
            <cacheFrom>${image.cache.from}</cacheFrom>
            <cacheTo>${image.cache.to}</cacheTo>
          </buildx>
        </build>
      </image>
    </images>
  </configuration>
</plugin>
```

### CI-only activation via a profile

Local developer builds stay untouched: the cache properties default to empty
and are only populated when the `CI` environment variable exists (GitHub
Actions sets `CI=true` automatically).

```xml
<properties>
  <image.cache.from></image.cache.from>
  <image.cache.to></image.cache.to>
</properties>

<profiles>
  <profile>
    <id>ci-image-cache</id>
    <activation>
      <property>
        <name>env.CI</name>
      </property>
    </activation>
    <properties>
      <image.cache.from>type=registry,ref=ghcr.io/your-org/your-app:buildcache</image.cache.from>
      <image.cache.to>type=registry,ref=ghcr.io/your-org/your-app:buildcache,mode=max</image.cache.to>
    </properties>
  </profile>
</profiles>
```

> **Note:** If your JKube version rejects empty `cacheFrom`/`cacheTo` values,
> move the cache elements entirely into the profile (duplicate the `<image>`
> block there), or pass the values from the workflow via `-D` properties.

### Multi-image reactor builds

Each image needs **its own cache ref** so builds don't overwrite each other:

```
ghcr.io/your-org/app-api:buildcache
ghcr.io/your-org/app-worker:buildcache
ghcr.io/your-org/app-gateway:buildcache
```

Convention: `ref=<image-name>:buildcache`, parameterized per module.

## GitHub Actions workflow

The job is a straight pipeline — note what's absent: no runtime-token helper
step, because the registry backend never touches GitHub's cache service.

```
┌────────────────────────────┐
│  actions/checkout@v4       │
└────────────────────────────┘
              |
              v
┌────────────────────────────┐
│  actions/setup-java@v4     │
│  (JDK 21 + ~/.m2 cache)    │
└────────────────────────────┘
              |
              v
┌────────────────────────────┐
│  setup-buildx-action@v3    │
│  (docker-container driver) │
└────────────────────────────┘
              |
              v
┌────────────────────────────┐
│  docker/login-action@v3    │
│  (GHCR + GITHUB_TOKEN)     │
└────────────────────────────┘
              |
              v
┌────────────────────────────┐
│  mvn package               │  <-- no runtime-token helper
│    k8s:build k8s:push      │      step needed (vs type=gha)
└────────────────────────────┘
```

```yaml
name: build

on:
  push:
    branches: [main]
  pull_request:

permissions:
  contents: read
  packages: write   # required to push images + cache to GHCR

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven            # caches ~/.m2/repository — often the bigger win

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
        # creates a docker-container driver builder; required for cache export

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push image
        run: mvn -B -ntp package k8s:build k8s:push
```

### Pull requests from forks

```
┏━[ WARNING: fork PRs ]━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃GITHUB_TOKEN on pull requests from forks is read-only. Both     ┃
┃cacheTo (export) and k8s:push will fail with "unauthorized".    ┃
┃Gate cache export and pushes on non-fork events, or build images┃
┃only on push to trusted branches.                               ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
```

Concretely: only set the `cacheTo` property when
`github.event.pull_request.head.repo.full_name == github.repository`, while
keeping `cacheFrom` (reads are fine).

## Dockerfile considerations

Layer caching only pays off if the Dockerfile is ordered for it — dependency
resolution before source copy:

```dockerfile
FROM <base-image> AS build
WORKDIR /app
# 1. Copy only the files that affect dependency resolution first
COPY pom.xml ./
COPY */pom.xml ./modules/        # adapt for multi-module layouts
RUN mvn -B -ntp dependency:go-offline
# 2. Then the sources — only this layer and below invalidate on code changes
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM <runtime-base-image>
COPY --from=build /app/target/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

What gets reused per kind of change:

| Dockerfile layer | `src/` change only | `pom.xml` change |
|---|---|---|
| `FROM <base-image>` | CACHED | CACHED |
| `COPY pom.xml ./` | CACHED | REBUILT |
| `RUN mvn dependency:go-offline` | CACHED ← big win | REBUILT |
| `COPY src ./src` | REBUILT | REBUILT |
| `RUN mvn package` | REBUILT | REBUILT |
| `COPY --from=build *.jar` | REBUILT | REBUILT |

With `mode=max` the intermediate `build` stage layers are exported too, so a
docs-only or test-only change reuses the dependency layer entirely.

If JKube generates the image (zero-config / generator mode with an assembly),
layering is already split between dependencies and application artifacts, and
the same mechanics apply.

## Verifying it works

On the second run, buildx output should show an import line followed by
`CACHED` markers:

```
#5 importing cache manifest from ghcr.io/your-org/your-app:buildcache
#7 [build 2/6] COPY pom.xml ./
#7 CACHED
#8 [build 3/6] RUN mvn -B -ntp dependency:go-offline
#8 CACHED
```

To confirm JKube is passing the flags at all, run with `-X` and look for
`--cache-from type=registry,…` / `--cache-to type=registry,…` in the logged
buildx invocation.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Cache export is not supported for the docker driver` | Builder uses the default driver | Add `docker/setup-buildx-action` (docker-container driver) |
| `unauthorized` on cache export | Token lacks `packages: write`, or fork PR context | Fix workflow `permissions`; gate `cacheTo` on non-fork events |
| Cache never hits | Cache ref overwritten by another image's build, or Dockerfile invalidates early layers | Per-image `:buildcache` refs; reorder Dockerfile (deps before sources) |
| Flags missing from buildx command | JKube version predates buildx `cacheFrom`/`cacheTo` | Upgrade JKube; confirm with `mvn k8s:build -X` |
| First build slower than before | Expected — `mode=max` export adds upload time on cold builds | Subsequent builds recoup it; `mode=min` if export time dominates |

## Maintenance

- The `:buildcache` ref updates in place and is safe to delete via the GHCR
  package UI or API at any time; the next build runs cold and re-exports.
- For long-lived repos, schedule cleanup of untagged GHCR versions
  (e.g. `actions/delete-package-versions`).
- For multi-arch later (`linux/arm64` for Jetson-class targets), add the
  platform to `<platforms>`; the registry backend handles multi-platform
  cache manifests cleanly.

## Summary

1. Add a `<buildx>` block with `cacheFrom`/`cacheTo` of `type=registry`
   pointing at a `:buildcache` ref in GHCR.
2. Gate the cache properties behind a CI-activated Maven profile.
3. Workflow: `setup-buildx-action` + `login-action` + `mvn k8s:build k8s:push`.
4. Pair with `setup-java`'s Maven dependency cache.
5. Order the Dockerfile so dependency layers precede source layers.
