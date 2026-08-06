// Section 508 / WCAG 2.0 AA acceptance test for the Pipeline Studio root
// shell. Renders App, runs axe-core, asserts zero violations of the
// wcag2a + wcag2aa ruleset.
//
// This lives at .a11y.test.tsx so the standard `test` script does not
// pull it in — run via `pnpm run test:a11y`.

import { render } from "@testing-library/react";
import { axe, toHaveNoViolations } from "jest-axe";
import { describe, expect, it } from "vitest";
import { App } from "./App";

expect.extend(toHaveNoViolations);

describe("App accessibility (Section 508 / WCAG 2.0 AA)", () => {
    it("root shell has no axe-detectable violations", async () => {
        const { container } = render(<App />);
        const results = await axe(container, {
            runOnly: {
                type: "tag",
                values: ["wcag2a", "wcag2aa"]
            }
        });
        expect(results).toHaveNoViolations();
    });
});
