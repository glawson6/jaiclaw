# RESOLVED: 2 Spock stub tests intermittently failed in jaiclaw-tools-github after Embabel 1.5.0 bump

**Area:** `extensions/jaiclaw-tools-github/src/main/java/io/jaiclaw/tools/github/tools/GithubCommentTool.java` + `src/test/groovy/.../GithubCommentToolSpec.groovy`
**Severity:** low — 2/31 tests in one module, intermittent on isolated runs, unrelated to production functionality
**Trigger:** switching `embabel-agent.version` to `1.5.0-SNAPSHOT` / `1.5.0` on the `spring-boot-4-upgrade` branch (2026-08-10)
**Status: RESOLVED 2026-08-13.** Root cause diagnosed and fixed. 10/10 isolated runs green after the fix.

## Root cause

`GHIssue` in `github-api 1.330` declares **two `public` overloads** of `comment(String)` with identical erased param signatures:

```
public void comment(String) throws IOException
public GHIssueComment comment(String) throws IOException   ← the one production calls
```

The JVM distinguishes them by full method descriptor (return type is part of the descriptor), so production `invokevirtual GHIssue.comment:(Ljava/lang/String;)Lorg/kohsuke/github/GHIssueComment;` dispatches correctly. Confirmed by `javap -c` on the compiled `GithubCommentTool.class`.

Spock's byte-buddy mock proxy keys stubs on `(name, param-types)` — the return type is **not** part of the key. When the test declares `issue.comment(_ as String) >> { -> comment }`, Spock registers a single return value that both overloads bind to. Method dispatch at invocation time picks between them non-deterministically. Roughly 1/3 of isolated runs bind to the `void` overload, which returns nothing, the stubbed value is discarded, and production sees `null` back from `issue.comment(body)`. NPE on `.getHtmlUrl()`.

The Embabel 1.5.0 bump only *surfaced* this — the flake was latent under any recent `github-api` version. Something about the transitive tree shift changed byte-buddy's method-descriptor iteration order enough to make the ambiguous case fire noticeably. Under 2.0.0-SNAPSHOT and older Embabel versions, the order happened to consistently bind to the covariant overload.

## Fix

**Not on the test side, on the production side.** Every test-side mitigation we tried (rename fields, plain-object stubs, typed closure returns, cast the return, stub before setup) either had no effect or made the flake rate worse. The right fix is to give tests an **unambiguous mocking surface** — extract the ambiguous call into a protected helper method the test can mock via `Spy`.

### Production change — `GithubCommentTool.java`

```java
GHIssue issue = repo(repo).getIssue(number);
GHIssueComment comment = postComment(issue, body);   // was: issue.comment(body);
// ...
protected GHIssueComment postComment(GHIssue issue, String body) throws Exception {
    return issue.comment(body);
}
```

The extracted method's signature is unambiguous — one method, one return type. Tests mock `tool.postComment(...)` instead of `issue.comment(...)`. Zero runtime cost (one extra virtual call in production, elided by C2), zero API impact.

### Test change — `GithubCommentToolSpec.groovy`

```groovy
def tool = Spy(GithubCommentTool, constructorArgs: [clientProvider])
// ...
tool.postComment(issue, _ as String) >> comment      // unambiguous stub target
```

### Result

10 back-to-back isolated `mvn test -pl :jaiclaw-tools-github -o` runs: **10/10 pass**. Full-reactor `mvn test --fail-at-end`: 86 modules, 4,349 tests, 0 failures, 0 errors, 10 skipped.

## Investigation trail (kept for future overload-ambiguity debugging)

The path from "test fails" → "extract helper" took several dead ends worth recording:

- `mvn dependency:list -pl :jaiclaw-tools-github -o -DincludeScope=test` returns **byte-for-byte identical** output under 1.5.0-SNAPSHOT vs 2.0.0-SNAPSHOT. Same 86 resolved artifacts, same versions, same scopes, same byte-buddy 1.18.10, same mockito 5.23.0, same spock 2.4-groovy-5.0.
- `jaiclaw-tools-github/pom.xml` has **zero Embabel deps** — the module is not part of the Embabel graph at all.
- `embabel-agent-dependencies-1.5.0` vs `-2.0.0` POM diff: only additions to production BOM (`embabel-agent-zai-autoconfigure`, `embabel-agent-dashscope-autoconfigure`, similar starter shuffles). Nothing that touches test infra.
- Full-reactor test on 1.5.0 → this was the **only failing module** across 86 modules. Pipeline e2e, kanban demo, agentmind demo, and every Embabel-touching module passed.
- Renaming the class-field `def comment = Mock(...)` to a method-local `def postedComment = Mock(...)` **increased** the flake rate from ~1/3 to ~4/10. Ruled out parallel-thread field contention (surefire is `forkCount=1, reuseForks=true`, no parallel test execution).
- Switching the stub form from `>> { -> comment }` to `>> comment` **increased** the flake rate to 7/10. Ruled out the closure-return path as the dispatcher trigger.
- Bytecode inspection via `javap -c` confirmed production calls the covariant overload deterministically — the bug was purely in Spock's proxy dispatch.
- Adding `@Deprecated` or type-cast disambiguation at the call site (attempted) didn't help — Spock's proxy is descriptor-blind regardless of source-level cues.
- The extract-and-Spy fix worked on the first try. **10/10 green.**

## Lesson

When a Spock stub against an overloaded method fails intermittently, do not chase the stub form or the mock-scope. Instead:

1. Run `javap -p <ClassName>` on the mocked type and grep for duplicate method names with the same param types. If you find any, this is your bug.
2. Extract the production call site into a helper method whose signature is unambiguous.
3. Mock the helper via `Spy`.

Save several hours vs. the sequence above.

## Related

- `docs/spring-boot-4-upgrade/02-embabel-gate.md` — the version-selection reasoning that led here
- Embabel PR #1765 (1.5.0 line, opened 2026-07-06) — the upstream work that produced this release
- Bytecode reference: `javap -c /Users/tap/dev/workspaces/openclaw/jaiclaw/extensions/jaiclaw-tools-github/target/classes/io/jaiclaw/tools/github/tools/GithubCommentTool.class`
