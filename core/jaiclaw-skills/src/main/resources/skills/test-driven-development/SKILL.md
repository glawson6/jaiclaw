---
name: test-driven-development
description: Enforce RED-GREEN-REFACTOR TDD cycle
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# Test-Driven Development

Follow the strict RED → GREEN → REFACTOR cycle when writing code. Every behavioral change starts with a failing test.

## The TDD Cycle

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│   RED    │────▶│  GREEN   │────▶│ REFACTOR │
│          │     │          │     │          │
│ Write a  │     │ Write    │     │ Clean up │
│ failing  │     │ minimal  │     │ without  │
│ test     │     │ code to  │     │ changing │
│          │     │ pass     │     │ behavior │
└──────────┘     └──────────┘     └──────────┘
      ▲                                │
      └────────────────────────────────┘
```

## Phase 1: RED — Write a Failing Test

1. **Identify the behavior** — what should the code do? Express it as a test.
2. **Write the test first** — before any production code.
3. **Run the test** — confirm it fails for the expected reason (not a syntax error or import failure).
4. **One test at a time** — do not write multiple failing tests before making any pass.

```bash
# Run the test and confirm it FAILS
ShellExec: pytest tests/test_feature.py::test_new_behavior -x
```

The failure message should clearly indicate the missing behavior, not an infrastructure problem.

## Phase 2: GREEN — Make It Pass

1. **Write the minimum code** to make the failing test pass.
2. **Do not generalize** — no abstractions, no handling of edge cases not yet tested.
3. **Hardcode if needed** — it is fine to return a literal value if that passes the test. The next test will force you to generalize.
4. **Run all tests** — the new test must pass and no existing tests should break.

```bash
# Run ALL tests to verify nothing broke
ShellExec: pytest tests/ -x
```

## Phase 3: REFACTOR — Clean Up

1. **Improve structure** — extract functions, rename variables, remove duplication.
2. **Do not change behavior** — all tests must still pass after refactoring.
3. **Apply design principles** — SRP, DRY, clear naming.
4. **Run tests again** — confirm the refactor is behavior-preserving.

```bash
# Confirm refactor is safe
ShellExec: pytest tests/ -x
```

## Guidelines

### What Makes a Good Test

- **Descriptive name** — `test_login_rejects_expired_token` over `test_login_3`.
- **Arrange-Act-Assert** — clear setup, single action, specific assertion.
- **One logical assertion** — test one behavior per test function.
- **Independent** — no test depends on another test's state or execution order.

### When to Write the Next Test

Move to the next RED phase when:
- Current test passes (GREEN achieved).
- Code is clean (REFACTOR complete).
- You have identified the next missing behavior.

### Test Ordering Strategy

Progress from simple to complex:
1. **Happy path** — basic correct behavior.
2. **Edge cases** — empty inputs, boundary values, nulls.
3. **Error cases** — invalid inputs, failures, exceptions.
4. **Integration** — interactions between components.

### Triangulation

When a hardcoded return passes a test, add a second test with different inputs to force generalization:

```python
# Test 1: passes with hardcoded return "fizz"
def test_fizzbuzz_3():
    assert fizzbuzz(3) == "fizz"

# Test 2: forces real implementation
def test_fizzbuzz_6():
    assert fizzbuzz(6) == "fizz"
```

## Anti-Patterns to Avoid

| Anti-Pattern | Problem |
|-------------|---------|
| Writing tests after code | Tests confirm what you wrote, not what you need |
| Multiple failing tests at once | Lose focus, unclear which behavior to implement next |
| Skipping REFACTOR | Technical debt accumulates immediately |
| Testing implementation details | Tests break on refactor, defeating the purpose |
| Large steps | Each cycle should take minutes, not hours |

## Rules

1. **Never write production code without a failing test** — the test comes first, always.
2. **Write the simplest code that passes** — resist the urge to anticipate future tests.
3. **Run tests after every change** — RED, GREEN, and REFACTOR each end with a test run.
4. **Refactor only when green** — never change structure while tests are failing.
5. **Commit at green** — each passing cycle is a safe commit point.
