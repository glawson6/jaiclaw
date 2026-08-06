package io.jaiclaw.cli.github;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable GitHub slash-command dispatcher.
 *
 * <p>Two run modes:
 * <ul>
 *   <li>Non-interactive one-shot — invoked by a GitHub Actions workflow:
 *       {@code java -jar jaiclaw-cli-github.jar handle-comment --repo owner/name --issue 42 --body "/chat hi"}
 *       Spring Shell's non-interactive runner picks the {@code handle-comment}
 *       command, executes it, and exits.
 *   </li>
 *   <li>Interactive REPL — invoked with no args, boots into Spring Shell for
 *       local testing against a real repo (needs {@code GITHUB_TOKEN} +
 *       {@code ANTHROPIC_API_KEY} in the environment).
 *   </li>
 * </ul>
 *
 * <p>Scan base packages include {@code io.jaiclaw.shell} so the shared
 * {@code ChatCommands} bean from {@code jaiclaw-shell-commands} is
 * available in the REPL alongside the GitHub slash-commands.
 */
@SpringBootApplication(scanBasePackages = {"io.jaiclaw.cli.github", "io.jaiclaw.shell"})
public class JaiClawCliGithubApplication {

    public static void main(String[] args) {
        SpringApplication.run(JaiClawCliGithubApplication.class, args);
    }
}
