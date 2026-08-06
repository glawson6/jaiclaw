// Section 508 / WCAG 2.0 AA guardrails for the Pipeline Studio SPA.
// Runs `eslint-plugin-jsx-a11y` rules against every .tsx / .ts file
// under src/. See docs/compliance/section-508.md for the wider
// accessibility posture.
//
// Invocation: `pnpm run lint:a11y` — reports violations at the console.

import jsxA11y from "eslint-plugin-jsx-a11y";

export default [
    {
        files: ["src/**/*.{tsx,ts}"],
        plugins: {
            "jsx-a11y": jsxA11y
        },
        rules: {
            // Enable the recommended jsx-a11y ruleset (equivalent to the
            // `plugin:jsx-a11y/recommended` config in legacy ESLint syntax).
            ...jsxA11y.configs.recommended.rules
        }
    }
];
