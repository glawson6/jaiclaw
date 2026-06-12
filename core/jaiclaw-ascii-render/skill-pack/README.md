# ascii-rendering — Claude Skill pack

This directory is the source for an [Anthropic Claude Skill](https://www.anthropic.com/news/agent-skills)
that exposes the JaiClaw ASCII renderer to Claude (or any LLM that can
shell out via a code-execution tool).

It is **not** the same thing as the in-repo JaiClaw skill at
`core/jaiclaw-skills/src/main/resources/skills/ascii-rendering/`. That
one is loaded into JaiClaw agents' system prompt. *This* one is a
portable artifact you upload to claude.ai / Claude Desktop / Claude
Code.

## Layout

```
skill-pack/
├── SKILL.md                          ← Anthropic frontmatter + usage docs
├── scripts/
│   ├── RenderScene.java              ← JBang script: scene JSON → ASCII
│   └── RenderBox.java                ← JBang script: text → boxed ASCII
├── examples/
│   ├── boxed-hello.json
│   ├── call-graph.json
│   └── scatter-plot.json
└── build-skill-zip.sh                ← packages into dist/*.zip
```

## How the runtime path works

The two scripts use [JBang](https://www.jbang.dev) for both Java and
dependency management. The shebang `///usr/bin/env jbang "$0" "$@"`
makes each `.java` file directly executable. The `//DEPS` directive
points at `io.jaiclaw:jaiclaw-ascii-render`, which JBang resolves from
Maven the first time the script runs. The `//REPOS` directive adds the
TapTech Nexus so the current `-SNAPSHOT` version can resolve before
the library is published to Maven Central.

Once 0.8.1 is on Central, both scripts can drop the `//REPOS` line
and pin the released version in `//DEPS`.

## Building the upload zip

```bash
./build-skill-zip.sh
# → dist/ascii-rendering-skill-0.1.0.zip
```

Upload the zip via the claude.ai Skills UI or Claude Desktop's
"Install Skill" flow.

## Local smoke test (no Claude needed)

```bash
# Box shortcut
echo "Build green" | jbang scripts/RenderBox.java --title=STATUS --border=double

# Full scene
jbang scripts/RenderScene.java --file examples/boxed-hello.json
jbang scripts/RenderScene.java --file examples/call-graph.json
jbang scripts/RenderScene.java --file examples/scatter-plot.json
```

The first invocation downloads ~3 MB to `~/.jbang/cache/`; subsequent
calls start in under a second.

## Versioning

Bump `VERSION` (create the file or override via env) before each release
of the skill zip. The library jar version pinned in the scripts' `//DEPS`
should track the JaiClaw release the skill was built against.
