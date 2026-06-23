package io.jaiclaw.shell.commands.setup.steps;

import io.jaiclaw.shell.commands.setup.OnboardResult;
import io.jaiclaw.shell.commands.setup.WizardStep;
import org.springframework.shell.component.flow.ComponentFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wizard step that offers to scaffold a {@code .env.op.tpl} 1Password
 * template alongside the regular {@code .env}.
 *
 * <p>Skipped silently when:
 * <ul>
 *   <li>The flow is QuickStart (this is a manual-mode polish step).</li>
 *   <li>The {@code op} CLI isn't on PATH (we don't pester users who
 *       don't use 1Password).</li>
 *   <li>The operator answers "no" to the offer.</li>
 * </ul>
 *
 * <p>Strict no-leakage rules:
 * <ul>
 *   <li>Never asks for or stores a service-account token. The operator
 *       authenticates via {@code op signin} independently.</li>
 *   <li>Never prints existing secret values. Only env-var NAMES are
 *       displayed.</li>
 *   <li>The actual template write happens in {@link FinalizationStep}
 *       so it lands atomically alongside the regular config files.</li>
 * </ul>
 *
 * <p>Operators wanting finer per-key control (custom item/field
 * mappings) should use the {@code bin/jaiclaw setup-1password}
 * fast-path command instead — this step intentionally uses sensible
 * defaults derived from the env-var name (prefix → item, suffix →
 * field) to keep the wizard flow short.
 */
public final class OnePasswordStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public OnePasswordStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "1Password";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // QuickStart skips — keeps the fast path fast.
        if (!result.isManual()) {
            return true;
        }

        if (!opAvailable()) {
            // No 'op' CLI on PATH — silently skip. The bash command
            // bin/jaiclaw setup-1password can be run later if the
            // operator installs 'op' after JaiClaw.
            return true;
        }

        ComponentFlow offer = flowBuilder.clone().reset()
                .withConfirmationInput("op-enabled")
                    .name("Generate a 1Password template (.env.op.tpl) alongside .env?")
                    .defaultValue(false)
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult offerResult = offer.run();
        Boolean enabled = WizardStep.getOrNull(offerResult.getContext(), "op-enabled", Boolean.class);
        if (!Boolean.TRUE.equals(enabled)) {
            return true;
        }

        ComponentFlow vaultFlow = flowBuilder.clone().reset()
                .withStringInput("vault")
                    .name("1Password vault name:")
                    .defaultValue("")
                    .and()
                .build();
        ComponentFlow.ComponentFlowResult vaultResult = vaultFlow.run();
        String vault = WizardStep.getOrNull(vaultResult.getContext(), "vault", String.class);
        if (vault == null || vault.isBlank()) {
            System.out.println("  No vault specified — skipping 1Password template.");
            return true;
        }

        // Default migration set: the env vars the wizard is about to
        // emit anyway. Channel-specific ones are only added when the
        // operator enabled that channel earlier in the flow.
        List<String> envKeys = new ArrayList<>();
        envKeys.add("JAICLAW_API_KEY");
        if (result.llmApiKey() != null) {
            // The provider-specific key name follows the existing
            // EnvFileWriter convention.
            switch (result.llmProvider()) {
                case "anthropic" -> envKeys.add("ANTHROPIC_API_KEY");
                case "openai"    -> envKeys.add("OPENAI_API_KEY");
                case "gemini"    -> envKeys.add("GEMINI_API_KEY");
                default -> { /* ollama / bedrock have no key */ }
            }
        }
        if (result.telegram() != null && result.telegram().enabled()) {
            envKeys.add("TELEGRAM_BOT_TOKEN");
        }
        if (result.slack() != null && result.slack().enabled()) {
            envKeys.add("SLACK_BOT_TOKEN");
            envKeys.add("SLACK_SIGNING_SECRET");
            if (result.slack().appToken() != null) {
                envKeys.add("SLACK_APP_TOKEN");
            }
        }
        if (result.discord() != null && result.discord().enabled()) {
            envKeys.add("DISCORD_BOT_TOKEN");
        }

        System.out.println("  Will scaffold " + envKeys.size()
                + " reference(s) under vault '" + vault + "':");
        for (String k : envKeys) {
            System.out.println("    " + k);
        }
        System.out.println("  Edit the generated .env.op.tpl to fine-tune item/field paths,");
        System.out.println("  or use 'jaiclaw setup-1password' for an interactive walkthrough.");

        result.setOnePassword(new OnboardResult.OnePasswordConfig(vault, envKeys));
        return true;
    }

    /** True iff {@code op --version} executes cleanly. */
    private static boolean opAvailable() {
        try {
            Process p = new ProcessBuilder("op", "--version")
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
