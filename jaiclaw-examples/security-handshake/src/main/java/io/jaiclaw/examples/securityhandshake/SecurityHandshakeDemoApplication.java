package io.jaiclaw.examples.securityhandshake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demonstrates an LLM-driven security handshake with configurable trust model.
 *
 * <h2>Trust Model</h2>
 * <p>Three configurable bootstrap trust levels:
 * <ul>
 *   <li><b>API_KEY</b> — Pre-shared key validates the client before ECDH proceeds</li>
 *   <li><b>CLIENT_CERT</b> — Client proves identity via pre-registered public key</li>
 *   <li><b>MUTUAL</b> — Both sides prove identity (API key + server signs nonce)</li>
 * </ul>
 *
 * <h2>Flow</h2>
 * <pre>
 *   LLM
 *    │
 *    ├─ 1. security_advertise_capabilities  ──► MCP server capabilities + trust level
 *    ├─ 2. security_generate_keypair        ──► EC key pair for ECDH
 *    ├─ 3. security_negotiate_session       ──► ECDH key exchange (+ bootstrap credential)
 *    ├─ 4. security_challenge_response      ──► nonce challenge from MCP server
 *    ├─ 5. security_verify_identity         ──► HMAC signature verification
 *    ├─ 6. security_establish_context       ──► session token (JWT)
 *    │
 *    │  ── LLM now has a Bearer token ──
 *    │
 *    └─ 7. protected_get_secret_data        ──► uses Bearer token, MCP server validates
 * </pre>
 *
 * <h2>Running</h2>
 * <pre>
 * # LOCAL mode (default, both sides in-process)
 * ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jaiclaw-examples/security-handshake
 *
 * # With custom API key for bootstrap trust
 * ANTHROPIC_API_KEY=sk-ant-... HANDSHAKE_API_KEY=my-secret-key \
 *   ./mvnw spring-boot:run -pl jaiclaw-examples/security-handshake
 * </pre>
 */
@SpringBootApplication
public class SecurityHandshakeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityHandshakeDemoApplication.class, args);
    }
}
