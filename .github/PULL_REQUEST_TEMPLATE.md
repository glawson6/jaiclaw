<!--
Thanks for the PR! A few short sections below get reviewers to a decision
faster. Delete any section that doesn't apply (e.g., remove the
"Breaking changes" section if there aren't any).
-->

## Summary

<!-- 1-3 sentences. What changes and why. -->

## Related issue

<!-- Closes #123, Refs #456 — or "N/A" if this is unsolicited. -->

## Type of change

- [ ] Bug fix (non-breaking)
- [ ] Feature (non-breaking, adds capability)
- [ ] Breaking change (API, config, or behavior change that requires migration notes)
- [ ] Documentation only
- [ ] Build / CI / release infrastructure

## Test plan

<!--
What commands did you run? E.g.:
  ./mvnw test -pl :jaiclaw-tools -am -o
  E2E_SCENARIOS=6 ./e2e/run-e2e-tests.sh
  manual run of the affected example
Paste relevant output excerpts if helpful.
-->

## Multi-tenancy conformance

<!--
If your change touches persistence, async work, or new ConcurrentHashMap
fields holding business data, confirm:
-->

- [ ] Reads/writes go through `TenantGuard.resolveStorageKey(...)`
- [ ] Async paths wrap with `TenantContextPropagator.wrap(...)`
- [ ] New `ConcurrentHashMap<String, ...>` fields are either tenant-scoped or annotated `@TenantAgnostic(reason="...")`
- [ ] `TenantIsolationGuardSpec` still passes
- [ ] N/A — this PR doesn't touch shared state

See `CONTRIBUTING.md` § "Multi-tenancy conformance check".

## Documentation

- [ ] `CLAUDE.md` updated (if directory layout / module count / dependency graph changed)
- [ ] `docs/INDEX.md` and the relevant guide updated (if user-facing behavior changed)
- [ ] `docs/dev/ARCHITECTURE.md` updated (if system-level)
- [ ] Release notes drafted in `releases/release-X.Y.Z.md` (if shipping in a tagged release)
- [ ] N/A

## Checklist

- [ ] Tests added/updated and passing
- [ ] No `Co-Authored-By: Claude` (or other AI attribution) in commits
- [ ] Code uses explicit types (no `var` in main sources)
- [ ] If an example was touched, `application.yml` has `jaiclaw.skills.allow-bundled` configured and the POM includes `jaiclaw-maven-plugin`
