import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

// Read the stylesheet directly from disk. Vite's `?raw` suffix is
// unreliable under Vitest's jsdom environment (returned empty in
// smoke) — fs.readFileSync sidesteps the CSS plugin entirely and
// gives us the exact source text the build ships.
const HERE = dirname(fileURLToPath(import.meta.url));
const cssText = readFileSync(
    resolve(HERE, "../src/styles/app.css"),
    "utf8"
);

/**
 * Lock spec for the theming contract in `src/styles/app.css`.
 *
 * The Studio ships CSS that must work in three mount modes:
 *   1. Standalone dev server (mounted at document root)
 *   2. As-a-whole-page in an adopter's Boot app (also document root)
 *   3. Embedded inside another React SPA via `react-shadow` (shadow root)
 *
 * These assertions prevent regressions that break mode 3 (which is what
 * TapCRM hit before this fix — see the deleted issue
 * `docs/issues/studio-stylesheet-shadow-dom-and-theming.md`).
 *
 * A snapshot would be brittle across whitespace changes; instead each
 * assertion targets a *contract-shaped* piece of the file.
 */
describe("app.css theming contract", () => {
    it("declares palette tokens on both :host and :root for shadow-DOM + standalone modes", () => {
        // The compound selector `:host, :root { --bg: ... }` is what
        // makes the palette resolve in both mount modes. Whitespace-tolerant.
        expect(cssText).toMatch(/:host\s*,\s*\n?\s*:root\s*\{/);
    });

    it("declares container reset on :host + html/body/#root", () => {
        // The reset that gives the Studio a background + text color needs
        // to fire in both mount modes. Shadow-root: :host. Document root:
        // html/body/#root. All four in one compound rule.
        expect(cssText).toMatch(
            /:host\s*,\s*\n?\s*html\s*,\s*\n?\s*body\s*,\s*\n?\s*#root\s*\{/
        );
    });

    it("uses height:100% (not 100vh) so shadow-host containers can size the Studio", () => {
        // No 100vh anywhere — height must be relative so both standalone
        // (where html/body get sized by the browser) and embedded
        // (where the shadow host div sets the height) modes work.
        expect(cssText).not.toMatch(/height:\s*100vh/);
    });

    it("provides a dark-theme override via [data-theme='dark']", () => {
        // Adopters flip `<html data-theme='dark'>` and the Studio follows.
        expect(cssText).toMatch(/\[data-theme=['"]dark['"]\]/);
    });

    it("provides a prefers-color-scheme dark fallback", () => {
        // When no explicit theme is set, follow the browser preference.
        expect(cssText).toMatch(/@media\s*\(prefers-color-scheme:\s*dark\)/);
    });

    it("bumps --text-dim to WCAG-AA-passing values (no more #94a3b8)", () => {
        // The old value failed AA at 3.4:1 on --panel: #1e293b. It must
        // not reappear anywhere in the stylesheet — dark palette uses
        // #cbd5e1 (~7:1), light palette uses #475569 (~8.6:1).
        expect(cssText).not.toMatch(/--text-dim:\s*#94a3b8/i);
    });

    it("sets :host { display: block } so the shadow host renders as a layout box", () => {
        // Custom-element default is `display: inline`, which collapses
        // height to 0 and the whole Studio disappears inside a shadow root.
        expect(cssText).toMatch(/:host\s*\{\s*display:\s*block/);
    });
});
