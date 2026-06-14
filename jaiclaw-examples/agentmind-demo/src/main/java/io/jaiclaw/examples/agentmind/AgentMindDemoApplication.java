package io.jaiclaw.examples.agentmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * End-to-end demo of the three AgentMind pillars wired together:
 *
 * <ul>
 *   <li><b>Soul</b> — per-agent voice + optional tenant overlay
 *       ({@code jaiclaw.agentmind.soul.enabled=true}).</li>
 *   <li><b>Memory</b> — per-user blob compaction across turns
 *       ({@code jaiclaw.agentmind.memory.enabled=true}).</li>
 *   <li><b>Tendencies</b> — observed style learned across turns
 *       ({@code jaiclaw.agentmind.tendencies.enabled=true}); default
 *       provider is the in-process heuristic learner.</li>
 *   <li><b>Persona overlays</b> — five curated personas selectable per
 *       session via the {@code personality} agent tool
 *       ({@code jaiclaw.agentmind.soul.personas.enabled=true}).</li>
 * </ul>
 *
 * <p>Run it with:
 * <pre>
 * ANTHROPIC_API_KEY=sk-ant-... \
 *   ./mvnw spring-boot:run -pl :jaiclaw-example-agentmind-demo
 *
 * # Send a message — Soul + Memory + Tendencies all participate.
 * curl -X POST http://localhost:8080/api/chat \
 *   -H 'Content-Type: application/json' \
 *   -d '{"sessionId":"sess-1","message":"Hi, I prefer concise bullet replies."}'
 *
 * # Ask the agent to switch persona (uses the `personality` tool).
 * curl -X POST http://localhost:8080/api/chat \
 *   -H 'Content-Type: application/json' \
 *   -d '{"sessionId":"sess-1","message":"Switch to the pirate persona."}'
 * </pre>
 *
 * <p>Persona files live in {@code src/main/resources/personas/}. State persists
 * under {@code ~/.jaiclaw/agentmind/} unless overridden via the rootDir
 * properties.
 */
@SpringBootApplication
public class AgentMindDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentMindDemoApplication.class, args);
    }
}
