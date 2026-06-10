package io.jaiclaw.examples.helloworld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The smallest possible JaiClaw program. Booting this class:
 *
 * <ol>
 *   <li>Stands up the JaiClaw gateway and agent runtime (via
 *       {@code jaiclaw-spring-boot-starter}).</li>
 *   <li>Registers the {@code EchoTool} bean defined in
 *       {@link HelloWorldConfig} as a callable tool.</li>
 *   <li>Exposes {@code POST /api/chat} for sending messages to the agent.</li>
 * </ol>
 *
 * <p>Run it with:
 * <pre>
 * ANTHROPIC_API_KEY=sk-ant-... \
 *   ./mvnw spring-boot:run -pl :jaiclaw-example-hello-world
 *
 * curl -X POST http://localhost:8080/api/chat \
 *   -H 'Content-Type: application/json' \
 *   -d '{"sessionId":"hello","message":"Use the echo tool to echo back: hi there!"}'
 * </pre>
 */
@SpringBootApplication
public class HelloWorldApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloWorldApplication.class, args);
    }
}
