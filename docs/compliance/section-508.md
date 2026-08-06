# Section 508 (Accessibility)

## What it is

Section 508 of the Rehabilitation Act (29 U.S.C. § 794d, as refreshed in 2018) requires federal agencies to ensure that the electronic and information technology they develop, procure, maintain, or use is accessible to people with disabilities. The 2018 refresh binds Section 508 to **WCAG 2.0 Level AA** (Web Content Accessibility Guidelines).

For a JaiClaw-based deployment, Section 508 applies when the deployment serves federal end users through a human-facing interface — a browser UI, a shell, a document, an API response that gets rendered in a UI.

## What JaiClaw contributes

JaiClaw ships three human-facing surfaces plus supporting infrastructure. Each has been remediated to meet Section 508 requirements:

| Surface | Status | Location |
|---|---|---|
| **Pipeline Dashboard** (server-rendered HTML) | ✅ Accessible | `extensions/jaiclaw-pipeline-dashboard/src/main/resources/jaiclaw-pipeline-dashboard/dashboard.html` |
| **Pipeline Studio SPA** (React + ReactFlow) | ✅ Accessible + linted | `apps/jaiclaw-pipeline-studio/frontend/` |
| **ASCII renderer** (LLM-callable tool) | ✅ Alt-text support | `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/AsciiRenderTool.java` |
| **CLI / TUI** (`jaiclaw-cli`, `jaiclaw-shell`) | ✅ Accessible (symbol + text, works monochrome) | `apps/jaiclaw-cli/`, `apps/jaiclaw-shell/` |
| **install.sh** (curl-installable) | ✅ Symbols + text, works without color | `install.sh` |
| **Markdown documentation** | ✅ Proper heading hierarchy, no color-only meaning | `docs/` |

### Detailed contributions

**Pipeline Dashboard:**
- Semantic HTML5 elements (`<header>`, `<main>`, `<aside>`, `<section>`, `<table>`, `<form>`)
- ARIA landmarks + `role="main"`, `role="navigation"`
- Form inputs have explicit `<label>` elements with `htmlFor` binding
- Connection status indicator has `aria-label` + `aria-live="polite"` (announces state change to screen readers, not color-only)
- Table has proper `<thead>` and `<tbody>`
- No `<img>` elements without alt attributes

**Pipeline Studio SPA:**
- Native `<button>` and semantic form elements throughout — no divs-with-onclick
- ARIA labels on all interactive elements (search palette, YAML editor, test-run input, canvas nodes, inspector panel)
- ARIA landmarks (`role="banner"`, `role="main"`, `role="complementary"`) wrap top-level regions
- `eslint-plugin-jsx-a11y` + `axe-core` + `@axe-core/react` gate the frontend build
- CI runs `pnpm run test:a11y` on every build
- Focus states styled (not relying on browser defaults)

**ASCII renderer:**
- `altText` input schema field on `AsciiRenderTool` — LLM callers can (and are prompted to) supply a text description of every diagram
- Alt text propagates to `ToolResult.Success.metadata` where downstream consumers (MCP clients, chat renderers, Canvas HTML wrappers) can surface it
- Callers building A2UI HTML wrappers around ASCII output can inject the alt-text into `<img alt="...">` or `<figure><figcaption>` structures

**CLI / TUI:**
- Symbolic markers (`✓`, `✗`, `!`, `▸`) accompany every color-coded status message
- Works correctly in monochrome terminals
- No color-only meaning anywhere in status output
- `install.sh` reports progress as human-readable text (not spinners or bars alone)

## How to enable / disable

**Default:** all Section 508 remediations ship active. The dashboard `aria-label`, ARIA landmarks, and `altText` schema field impose zero runtime cost — they're static HTML/JSON/schema attributes that benefit accessibility consumers whether adopters ask for them or not.

The dev-time linting (`eslint-plugin-jsx-a11y`, `axe-core`) runs during Pipeline Studio SPA builds/tests only if adopters build the SPA. To skip the a11y test in local dev:

```bash
# Skip the a11y test in local iteration; still runs in CI
pnpm test  # standard tests only
# NOT pnpm run test:a11y
```

To disable the JSX a11y lint rules entirely (not recommended for a federal-user-facing deployment):

```javascript
// Remove or comment-out the a11y ruleset in eslint.config.mjs
```

Framework code changes are always-on and cannot be disabled — they represent baseline accessibility hygiene that shipping without would be a regression, not a feature.

## What's adopter responsibility

Section 508 is a property of a **deployed system**, not a library. Adopters must:

1. **Test with real assistive technology** — JAWS, NVDA, and VoiceOver on the final deployment. Automated linting catches common issues but does not replace user testing.
2. **Publish an ACR** (Accessibility Conformance Report) — VPAT template from ITI, filled out against the adopter's finished product
3. **Address any user-reported regressions** — accessibility issues sometimes surface only in production usage
4. **When wrapping JaiClaw HTML output in a larger UI**, ensure the wrapping UI is also compliant
5. **When generating HTML dynamically via LLM output** (Canvas / A2UI extension), post-process or gate on an accessibility linter — the framework cannot enforce accessibility on LLM-generated markup

## What's out of scope

- WCAG 3.0 (still a draft)
- ADA Title III applicability (that's a legal question for the deployment context)
- Specific-agency 508 variances or waivers

## How to verify

**Pipeline Dashboard:**
```bash
# Boot the dashboard
./mvnw spring-boot:run -pl :jaiclaw-gateway-app
# Then browse to http://localhost:8080/pipelines/dashboard
# Open browser devtools → Accessibility inspector → check landmarks + ARIA
# Manual test: unplug mouse, navigate with Tab/Shift+Tab/Enter
```

**Pipeline Studio SPA:**
```bash
cd apps/jaiclaw-pipeline-studio/frontend
pnpm install
pnpm run test:a11y
# axe-core should report 0 violations
```

**ASCII renderer:**
```bash
# The altText field is in the tool's input schema — verify:
grep -n "altText" core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/AsciiRenderTool.java
```

**CLI:**
```bash
# Run the install script with NO_COLOR set
NO_COLOR=1 ./install.sh
# All status messages remain readable — symbols + text carry meaning
```

## Related code files

- `extensions/jaiclaw-pipeline-dashboard/src/main/resources/jaiclaw-pipeline-dashboard/dashboard.html`
- `apps/jaiclaw-pipeline-studio/frontend/src/App.tsx`
- `apps/jaiclaw-pipeline-studio/frontend/package.json` (a11y linter deps)
- `apps/jaiclaw-pipeline-studio/frontend/.eslintrc.cjs`
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/AsciiRenderTool.java`
- `install.sh`

## References

- Section 508: [https://www.section508.gov/](https://www.section508.gov/)
- WCAG 2.0 Level AA: [https://www.w3.org/WAI/WCAG21/quickref/?currentsidebar=%23col_customize&levels=aaa](https://www.w3.org/WAI/WCAG21/quickref/)
- ACR / VPAT template: [https://www.itic.org/policy/accessibility/vpat](https://www.itic.org/policy/accessibility/vpat)
- axe-core: [https://github.com/dequelabs/axe-core](https://github.com/dequelabs/axe-core)
