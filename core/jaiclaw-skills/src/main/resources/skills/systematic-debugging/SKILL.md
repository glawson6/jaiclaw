---
name: systematic-debugging
description: Root-cause investigation methodology before applying fixes
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# Systematic Debugging

Follow a structured investigation process to find root causes before applying fixes. Never guess-and-check — gather evidence, form hypotheses, and verify before changing code.

## The Debugging Protocol

```
1. REPRODUCE  →  2. ISOLATE  →  3. DIAGNOSE  →  4. FIX  →  5. VERIFY
```

## Step 1: Reproduce

Establish a reliable reproduction before investigating.

- **Get the exact error** — copy the full stack trace, error message, and exit code.
- **Identify the trigger** — what input, action, or sequence causes the bug?
- **Determine frequency** — always, sometimes, only under specific conditions?
- **Capture the environment** — OS, runtime version, dependencies, configuration.

```bash
# Reproduce and capture full output
ShellExec: command_that_fails 2>&1 | tee /tmp/debug-output.txt
```

**Do not proceed until you can reliably trigger the bug.**

## Step 2: Isolate

Narrow down the problem space.

- **Binary search** — comment out or bypass half the code path, test, repeat.
- **Minimal reproduction** — strip away unrelated code until you have the smallest case that fails.
- **Check boundaries** — is the bug in your code, a dependency, the runtime, or the environment?
- **Diff against working state** — what changed between the last known-good state and now?

```bash
# Check recent changes
ShellExec: git log --oneline -20
ShellExec: git diff HEAD~5 -- path/to/suspected/file.py

# Bisect if the bug is a regression
ShellExec: git bisect start
ShellExec: git bisect bad HEAD
ShellExec: git bisect good v1.2.0
```

## Step 3: Diagnose

Form and test hypotheses about the root cause.

### Evidence Gathering

- **Read the code** — use **FileRead** to examine the suspected code path.
- **Add instrumentation** — insert logging or print statements at key points.
- **Check state** — inspect variable values, data structures, and control flow at the failure point.
- **Review logs** — look for warnings, errors, or unexpected patterns before the crash.

### Hypothesis Testing

For each hypothesis:

1. **State it clearly** — "I believe X is happening because of Y."
2. **Predict an observation** — "If this is correct, I should see Z."
3. **Test the prediction** — gather evidence that confirms or refutes.
4. **Record the result** — even negative results are valuable.

### Common Root Causes

| Category | Examples |
|----------|----------|
| **State** | Uninitialized variable, stale cache, race condition |
| **Input** | Unexpected null, encoding mismatch, off-by-one |
| **Environment** | Missing env var, wrong version, path issue |
| **Timing** | Race condition, timeout, async ordering |
| **Resources** | Memory exhaustion, file descriptor leak, disk full |
| **Dependencies** | Breaking API change, version conflict, missing module |

## Step 4: Fix

Apply the smallest correct change.

- **Fix the root cause** — not the symptom. If a null check hides the real problem, fix the source of the null.
- **One change at a time** — do not bundle unrelated fixes.
- **Preserve behavior** — the fix should change only the broken behavior, nothing else.
- **Add a test** — write a test that would have caught the bug.

## Step 5: Verify

Confirm the fix is correct and complete.

- **Run the reproduction** — the original failure must be resolved.
- **Run the full test suite** — no regressions.
- **Test edge cases** — variations of the original trigger.
- **Check related code** — could the same bug exist elsewhere (same pattern, copy-paste)?

```bash
# Run the specific failing test
ShellExec: pytest tests/test_module.py::test_that_was_failing -x

# Run the full suite
ShellExec: pytest tests/ -x
```

## Debugging Journal

Maintain a running log during investigation:

```
## Bug: [Short description]
### Reproduction: [How to trigger]
### Hypothesis 1: [Description]
  - Prediction: [What I expect to see]
  - Evidence: [What I actually observed]
  - Result: CONFIRMED / REFUTED
### Hypothesis 2: ...
### Root Cause: [Final diagnosis]
### Fix: [What was changed and why]
```

## Rules

1. **Reproduce before investigating** — if you cannot trigger the bug, you cannot verify a fix.
2. **Never guess-fix** — changing code to "see if it helps" without understanding the cause creates new bugs.
3. **Hypothesize explicitly** — write down what you think is wrong before testing.
4. **Smallest fix possible** — avoid refactoring or improving code during a bug fix.
5. **Test the fix, not your assumption** — verify the original reproduction, not just your added test.
