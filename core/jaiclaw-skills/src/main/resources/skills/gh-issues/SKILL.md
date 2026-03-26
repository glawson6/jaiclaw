---
name: gh-issues
description: "Fetch GitHub issues, analyze them, and assist with creating fixes and PRs. Use when asked to triage, fix, or batch-process GitHub issues. Requires GH_TOKEN or gh CLI auth."
alwaysInclude: false
requiredBins: [gh]
platforms: [darwin, linux]
---

# GitHub Issues Fixer

Fetch GitHub issues, analyze them, and help create targeted fixes with pull requests.

## Workflow

### 1. Fetch Issues

```bash
# List open issues
gh issue list --repo owner/repo --state open --json number,title,body,labels,assignees

# Filter by label
gh issue list --repo owner/repo --label bug --json number,title,body

# Filter by milestone
gh issue list --repo owner/repo --milestone "v1.0" --json number,title,body

# Filter by assignee
gh issue list --repo owner/repo --assignee @me --json number,title,body
```

### 2. Analyze an Issue

For each issue:
1. Read the issue body carefully
2. Search the codebase for relevant code using **Grep** and **Glob**
3. Assess confidence: is the fix clear and scoped?
4. If confidence is low, report findings and suggest manual review

### 3. Create a Fix Branch

```bash
git checkout -b fix/issue-{number} main
```

### 4. Implement the Fix

- Use **FileRead** to understand the current code
- Use **FileEdit** for targeted changes
- Follow existing code style and conventions
- Keep changes minimal and focused

### 5. Test

- Run existing test suite if available
- Verify the fix addresses the issue

### 6. Commit and Push

```bash
git add {changed_files}
git commit -m "fix: {short_description}

Fixes owner/repo#{number}"
git push -u origin fix/issue-{number}
```

### 7. Create PR

```bash
gh pr create \
  --title "fix: {title}" \
  --body "## Summary

{description_of_fix}

## Changes

{bullet_list}

## Testing

{what_was_tested}

Fixes #{number}"
```

## Safety Rules

- No force-push or modifying the base branch
- No unrelated changes or gratuitous refactoring
- No new dependencies without strong justification
- If the issue is unclear or too complex, report analysis instead of guessing
- Always confirm with the user before pushing or creating PRs

## Pre-flight Checks

Before processing each issue:
1. Check for existing PRs: `gh pr list --head fix/issue-{N}`
2. Check working tree: `git status --porcelain`
3. Verify remote access: `git ls-remote --exit-code origin HEAD`
