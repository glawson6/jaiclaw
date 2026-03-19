# Plan: Add Claude Code-style coding tools to JClaw (`jclaw-code` module)

## Context

JClaw already has a capable agent runtime (`AgentRuntime`), tool registry (`ToolRegistry`), and Spring AI bridge (`SpringAiToolBridge`). It also has basic file tools (`file_read`, `file_write`, `shell_exec`) in `jclaw-tools/builtin/`. However, these are bare-bones compared to Claude Code's developer toolset — there's no `Edit` (surgical string replacement), no `Glob` (fast file pattern matching), and no `Grep` (content search). The existing `file_write` overwrites entire files, and there's no way to search a codebase by filename or content.

**Goal**: Create a `jclaw-code` module that adds Claude Code-inspired developer tools — `file_edit`, `glob`, `grep` — to JClaw's tool registry. These tools plug into the existing agent loop via the standard `ToolCallback` SPI and become available to any agent with `CODING` or `FULL` profile.

**Non-goals**: We are NOT embedding Claude Code itself (no subprocess, no SDK dependency). We're implementing equivalent tool capabilities natively in Java using JClaw's existing tool infrastructure.

## What already exists (reuse, don't duplicate)

| Existing tool | File | Capabilities |
|---|---|---|
| `file_read` | `jclaw-tools/.../builtin/FileReadTool.java` | Read file with offset/limit, line numbers |
| `file_write` | `jclaw-tools/.../builtin/FileWriteTool.java` | Write entire file content |
| `shell_exec` | `jclaw-tools/.../builtin/ShellExecTool.java` | Execute shell command, capture output |
| `web_fetch` | `jclaw-tools/.../builtin/WebFetchTool.java` | Fetch URL content |
| `web_search` | `jclaw-tools/.../builtin/WebSearchTool.java` | DuckDuckGo search |

**Key infrastructure to reuse:**
- `AbstractBuiltinTool` — base class with `doExecute()`, `requireParam()`, `optionalParam()`, error handling
- `ToolCatalog` — section constants
- `ToolProfile.CODING` / `ToolProfile.MINIMAL` — existing profile tags
- `ToolContext.workspaceDir()` — workspace-relative path resolution
- `BuiltinTools.registerAll()` pattern — factory + batch registration
- `jclaw-browser/BrowserTools.java` pattern — static inner tool classes in a factory

## New tools to implement

### 1. `file_edit` — Surgical string replacement (new)

Like Claude Code's `Edit` tool. Finds a unique `old_string` in a file and replaces it with `new_string`. Fails if the string isn't found or isn't unique.

**Parameters:**
- `path` (required) — file path relative to workspace
- `old_string` (required) — exact text to find
- `new_string` (required) — replacement text
- `replace_all` (optional, default false) — replace all occurrences

**Behavior:**
- Read file → find `old_string` → verify uniqueness (unless `replace_all`) → replace → write back
- Return diff-like summary: line number(s) of change, snippet of surrounding context
- Error if `old_string` not found or found multiple times (when `replace_all=false`)

**Section:** `Files` | **Profiles:** `CODING`, `FULL`

### 2. `glob` — File pattern matching (new)

Like Claude Code's `Glob` tool. Finds files matching a glob pattern (e.g., `**/*.java`, `src/**/Test*.groovy`).

**Parameters:**
- `pattern` (required) — glob pattern
- `path` (optional) — directory to search in, relative to workspace (default: workspace root)

**Behavior:**
- Use `java.nio.file.FileSystem.getPathMatcher("glob:" + pattern)` with `Files.walk()`
- Return matching file paths, sorted, one per line
- Respect `.gitignore`-like exclusions (skip `target/`, `.git/`, `node_modules/`, `build/`)
- Cap results at 200 files to avoid flooding the LLM context

**Section:** `Files` | **Profiles:** `MINIMAL`, `CODING`, `FULL` (read-only)

### 3. `grep` — Content search (new)

Like Claude Code's `Grep` tool. Searches file contents for a regex pattern.

**Parameters:**
- `pattern` (required) — regex pattern
- `path` (optional) — directory or file to search in (default: workspace root)
- `glob` (optional) — file glob filter (e.g., `*.java`)
- `context` (optional, default 0) — lines of context around matches
- `max_results` (optional, default 50) — max matches to return

**Behavior:**
- Walk file tree, filter by glob if provided, search each file with `Pattern.compile()`
- Skip binary files (check first 512 bytes for null bytes)
- Skip common exclusion dirs (`target/`, `.git/`, `node_modules/`)
- Return: `filepath:line_number: matched_line` format (grep-style output)
- Include context lines when requested

**Section:** `Files` | **Profiles:** `MINIMAL`, `CODING`, `FULL` (read-only)

## Module structure

```
jclaw-code/
├── pom.xml
└── src/
    ├── main/java/io/jclaw/code/
    │   ├── CodeTools.java              — Factory: all() + registerAll()
    │   ├── FileEditTool.java           — Surgical edit
    │   ├── GlobTool.java               — File pattern search
    │   ├── GrepTool.java               — Content search
    │   └── CodeToolsAutoConfiguration.java — @AutoConfiguration to register tools
    ├── main/resources/META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/groovy/io/jclaw/code/
        ├── FileEditToolSpec.groovy
        ├── GlobToolSpec.groovy
        └── GrepToolSpec.groovy
```

## Detailed changes

### 1. `jclaw-code/pom.xml` (new)

Dependencies:
- `jclaw-core` — ToolCallback, ToolDefinition, ToolProfile, ToolResult, ToolContext
- `jclaw-tools` — AbstractBuiltinTool, ToolCatalog, ToolRegistry
- `spring-boot-autoconfigure` (optional) — for auto-config class
- `slf4j-api` — logging
- Test: `groovy`, `spock-core`, `gmavenplus-plugin`

### 2. `CodeTools.java` — Tool factory

```java
public final class CodeTools {
    private CodeTools() {}

    public static List<ToolCallback> all() {
        return List.of(
            new FileEditTool(),
            new GlobTool(),
            new GrepTool()
        );
    }

    public static void registerAll(ToolRegistry registry) {
        registry.registerAll(all());
    }
}
```

### 3. `FileEditTool.java` — Surgical string replacement

Extends `AbstractBuiltinTool`. Core logic:
- Read file as single string (`Files.readString()`)
- Count occurrences of `old_string` using `String.indexOf()` in a loop
- If count == 0 → Error("old_string not found")
- If count > 1 && !replace_all → Error("old_string found N times, provide more context or use replace_all")
- Replace via `String.replace()` (replace_all) or single `StringBuilder` replacement
- Write back via `Files.writeString()`
- Return: "Replaced N occurrence(s) in {path}" with a few lines of context around the edit

### 4. `GlobTool.java` — File pattern matching

Extends `AbstractBuiltinTool`. Core logic:
- Resolve base path from `context.workspaceDir()` + optional `path` param
- Create `PathMatcher` from `FileSystem.getPathMatcher("glob:" + pattern)`
- `Files.walk()` the directory tree
- Skip excluded dirs: `.git`, `target`, `build`, `node_modules`, `.idea`, `.gradle`
- Match relative paths against the pattern
- Sort results, cap at 200
- Return one path per line

### 5. `GrepTool.java` — Content search

Extends `AbstractBuiltinTool`. Core logic:
- Compile regex via `Pattern.compile(pattern)`
- Walk file tree, apply optional glob filter
- Skip binary files (check first 512 bytes for `\0`)
- Skip excluded dirs (same as glob)
- For each text file: read lines, match against pattern
- Format: `relative/path:lineNum: line content`
- Include N context lines before/after if `context` param > 0
- Cap at `max_results` matches total

### 6. `CodeToolsAutoConfiguration.java` — Spring Boot auto-config

```java
@AutoConfiguration
@AutoConfigureAfter(name = "io.jclaw.autoconfigure.JClawAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class CodeToolsAutoConfiguration {
    @Bean
    public CodeToolsRegistrar codeToolsRegistrar(ToolRegistry toolRegistry) {
        CodeTools.registerAll(toolRegistry);
        return new CodeToolsRegistrar();
    }
    public static class CodeToolsRegistrar {}
}
```

### 7. `AutoConfiguration.imports` (new)

```
io.jclaw.code.CodeToolsAutoConfiguration
```

### 8. Root `pom.xml` — Add module

Add `<module>jclaw-code</module>` to `<modules>` section and dependency entry to `<dependencyManagement>`.

### 9. `jclaw-bom/pom.xml` — Add dependency entry

Add `jclaw-code` to the BOM's `<dependencyManagement>`.

### 10. `jclaw-gateway-app/pom.xml` — Add dependency

Add `jclaw-code` as a dependency so the gateway app includes the tools.

### 11. `ToolCatalog.java` — No change needed

Existing `SECTION_FILES` is the right section for all three tools.

### 12. `ToolProfile` — No change needed

Existing `CODING` and `MINIMAL` profiles are sufficient.

## Test plan

### Unit tests (Spock)

**FileEditToolSpec:**
- Replaces unique string → success, file updated
- old_string not found → error
- old_string found multiple times, replace_all=false → error
- old_string found multiple times, replace_all=true → all replaced
- Preserves file encoding and line endings
- Path traversal attempt → error or safe resolution

**GlobToolSpec:**
- `**/*.java` finds Java files recursively
- `*.txt` finds only root-level txt files
- Skips `.git/` and `target/` directories
- Returns sorted results
- Caps results at 200
- Empty pattern matches → sensible default
- Non-existent base path → error

**GrepToolSpec:**
- Simple string pattern matches lines
- Regex pattern (e.g., `log\.\w+\(`) works
- `glob` filter restricts file types
- Context lines included when requested
- Binary files skipped
- Max results caps output
- Non-matching pattern → empty result (not error)

### Integration verification

```bash
# Build the new module
./mvnw compile -pl jclaw-code -o

# Run tests
./mvnw test -pl jclaw-code -o

# Install to local repo
./mvnw install -pl jclaw-code -DskipTests

# Verify tools appear in gateway
./start.sh shell
jclaw> tools    # Should list file_edit, glob, grep alongside existing tools
jclaw> chat "List all Java files in jclaw-core"   # Tests glob via agent
jclaw> chat "Find all usages of ToolRegistry"       # Tests grep via agent
```

## Files touched

| File | Action |
|---|---|
| `jclaw-code/pom.xml` | **New** — module POM |
| `jclaw-code/src/main/java/io/jclaw/code/CodeTools.java` | **New** — factory |
| `jclaw-code/src/main/java/io/jclaw/code/FileEditTool.java` | **New** — edit tool |
| `jclaw-code/src/main/java/io/jclaw/code/GlobTool.java` | **New** — glob tool |
| `jclaw-code/src/main/java/io/jclaw/code/GrepTool.java` | **New** — grep tool |
| `jclaw-code/src/main/java/io/jclaw/code/CodeToolsAutoConfiguration.java` | **New** — auto-config |
| `jclaw-code/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **New** |
| `jclaw-code/src/test/groovy/io/jclaw/code/FileEditToolSpec.groovy` | **New** — test |
| `jclaw-code/src/test/groovy/io/jclaw/code/GlobToolSpec.groovy` | **New** — test |
| `jclaw-code/src/test/groovy/io/jclaw/code/GrepToolSpec.groovy` | **New** — test |
| `pom.xml` | **Edit** — add module + dependencyManagement entry |
| `jclaw-bom/pom.xml` | **Edit** — add BOM entry |
| `jclaw-gateway-app/pom.xml` | **Edit** — add dependency |
