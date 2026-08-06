// Vitest config for the accessibility test suite (Section 508 / WCAG 2.0 AA).
// Runs axe-core against rendered components. Kept separate from the
// standard `test` script so the a11y suite can be triggered independently
// in CI (`pnpm run test:a11y`).

import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
    plugins: [react()],
    test: {
        environment: "jsdom",
        include: ["src/**/*.a11y.test.{ts,tsx}"],
        globals: true
    }
});
