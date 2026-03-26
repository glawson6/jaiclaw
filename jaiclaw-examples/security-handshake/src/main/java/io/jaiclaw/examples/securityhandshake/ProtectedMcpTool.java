package io.jaiclaw.examples.securityhandshake;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.security.HandshakeSession;
import io.jaiclaw.tools.security.HandshakeSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * A protected MCP server tool that requires a valid session token from the
 * security handshake. Simulates a real MCP server endpoint that validates
 * the Bearer token before returning sensitive data.
 *
 * <p>The LLM must first complete the security handshake to obtain a session
 * token, then pass it as the {@code sessionToken} parameter to this tool.
 */
public class ProtectedMcpTool implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(ProtectedMcpTool.class);

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "sessionToken": {
                  "type": "string",
                  "description": "Bearer session token obtained from the security_handshake tool"
                },
                "query": {
                  "type": "string",
                  "description": "What data to retrieve from the protected service"
                }
              },
              "required": ["sessionToken"]
            }""";

    private final HandshakeSessionStore sessionStore;

    public ProtectedMcpTool(HandshakeSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "protected_get_secret_data",
                "Retrieve sensitive data from the protected MCP server. "
                        + "REQUIRES a valid sessionToken obtained by calling the security_handshake tool. "
                        + "Call security_handshake first, then pass the returned sessionToken to this tool.",
                "Protected MCP",
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String sessionToken = (String) parameters.get("sessionToken");
        String query = parameters.containsKey("query") ? (String) parameters.get("query") : "default";

        // ── Gate: no token ──
        if (sessionToken == null || sessionToken.isBlank()) {
            log.warn("Protected tool called without session token");
            return new ToolResult.Error(
                    "ACCESS DENIED: No session token provided. "
                            + "You must complete the security handshake first by calling security_handshake.");
        }

        // ── Gate: validate token against session store ──
        var maybeSession = sessionStore.findByToken(sessionToken);
        if (maybeSession.isEmpty()) {
            log.warn("Protected tool called with invalid session token");
            return new ToolResult.Error(
                    "ACCESS DENIED: Invalid or expired session token. "
                            + "Please complete a new security handshake.");
        }

        HandshakeSession session = maybeSession.get();
        log.info("ACCESS GRANTED — authenticated as '{}' via handshake {} (cipher: {})",
                session.getVerifiedSubject(), session.getHandshakeId(), session.getSelectedCipherSuite());

        // ── Success: return protected data ──
        return new ToolResult.Success(String.format("""
                {
                  "status": "ACCESS GRANTED",
                  "authenticatedAs": "%s",
                  "handshakeId": "%s",
                  "cipherSuite": "%s",
                  "query": "%s",
                  "data": {
                    "secrets": [
                      "Project codename: AURORA",
                      "Launch date: 2026-06-15",
                      "Budget allocation: $2.4M",
                      "Team size: 12 engineers"
                    ],
                    "message": "This data was only accessible because you completed the security handshake and presented a valid session token."
                  }
                }""",
                session.getVerifiedSubject(),
                session.getHandshakeId(),
                session.getSelectedCipherSuite(),
                query));
    }
}
