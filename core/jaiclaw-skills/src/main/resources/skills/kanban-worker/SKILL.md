---
name: kanban-worker
description: Task lifecycle management and workspace handoff for Kanban workers
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# Kanban Worker

Execute individual tasks assigned by a Kanban orchestrator. Workers receive a task card, perform the work, and produce structured output for review.

## Task Lifecycle

```
Received → Acknowledged → Working → Output Ready → Handed Off
```

1. **Received** — parse the task card (ID, description, acceptance criteria).
2. **Acknowledged** — confirm understanding, note any ambiguities.
3. **Working** — execute the task using available tools.
4. **Output Ready** — produce deliverables and a completion report.
5. **Handed Off** — return results to the orchestrator.

## Input Format

Workers receive tasks as structured prompts:

```
TASK-ID: TASK-042
TITLE: Implement password strength meter
DESCRIPTION: Add a real-time password strength indicator to the signup form.
ACCEPTANCE CRITERIA:
- Shows strength levels: weak, fair, strong
- Updates on each keystroke
- Uses zxcvbn library for scoring
WORKSPACE: /path/to/project
```

## Execution Guidelines

- **Stay scoped** — only do what the task card describes. Do not refactor surrounding code or add unrequested features.
- **Use the workspace** — operate within the specified working directory.
- **Document changes** — list every file created or modified.
- **Test when possible** — run existing tests after changes. Write new tests if acceptance criteria require them.

## Output Format

When work is complete, produce a structured completion report:

```yaml
task_id: TASK-042
status: completed | blocked | needs_clarification
files_changed:
  - src/components/PasswordStrength.tsx (created)
  - src/components/SignupForm.tsx (modified)
summary: |
  Added PasswordStrength component using zxcvbn.
  Integrated into SignupForm with real-time updates.
tests_run: 4 passed, 0 failed
blockers: []
notes: |
  zxcvbn adds ~400KB to bundle. Consider lazy-loading
  if bundle size is a concern.
```

## Rules

1. **Acknowledge before working** — confirm you understand the task before starting.
2. **Do not exceed scope** — if you discover adjacent work needed, note it as a new task suggestion rather than doing it.
3. **Report blockers immediately** — if you cannot proceed, set status to `blocked` with a clear explanation.
4. **Preserve workspace state** — do not delete files, reset git, or make destructive changes outside the task scope.
5. **Include all artifacts** — every file path, test result, and observation goes in the completion report.
