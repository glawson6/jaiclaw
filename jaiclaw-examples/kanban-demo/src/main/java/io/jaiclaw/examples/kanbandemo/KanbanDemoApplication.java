package io.jaiclaw.examples.kanbandemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable Spring Boot app demonstrating the {@code jaiclaw-kanban}
 * extension end-to-end. Plan §9 group 6 / analysis §5.3.
 *
 * <p>Boots with a single fixture board (see
 * {@code src/main/resources/jaiclaw/kanban/boards/demo.yaml}) and a stub
 * agent runner so the demo runs without any LLM key. Exercises:
 *
 * <ul>
 *   <li>REST endpoints — boards, cards, transitions, snapshot, ASCII</li>
 *   <li>SSE live event stream</li>
 *   <li>Actuator endpoint at {@code /actuator/kanban}</li>
 *   <li>Column processors firing the stub runner on entry into
 *       {@code drafting}</li>
 *   <li>Idempotency: the runner's result is cached in the effect ledger
 *       and replayed on retry</li>
 * </ul>
 *
 * <p>Default port {@code 8200}. See {@code README.md} for the full
 * curl + SSE + ASCII walkthrough.
 */
@SpringBootApplication
public class KanbanDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(KanbanDemoApplication.class, args);
    }
}
