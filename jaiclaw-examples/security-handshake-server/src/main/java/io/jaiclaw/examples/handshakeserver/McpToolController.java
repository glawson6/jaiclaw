package io.jaiclaw.examples.handshakeserver;

import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.tools.security.HandshakeSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for protected MCP tool invocations.
 * Validates the {@code Authorization: Bearer} header against
 * {@link HandshakeSessionStore} before dispatching to the tool provider.
 */
@RestController
@RequestMapping("/mcp/data/tools")
public class McpToolController {

    private static final Logger log = LoggerFactory.getLogger(McpToolController.class);

    private final HandshakeSessionStore sessionStore;
    private final ProtectedDataMcpProvider toolProvider;

    public McpToolController(HandshakeSessionStore sessionStore,
                             ProtectedDataMcpProvider toolProvider) {
        this.sessionStore = sessionStore;
        this.toolProvider = toolProvider;
    }

    @PostMapping("/{toolName}")
    public ResponseEntity<?> executeTool(@PathVariable String toolName,
                                         @RequestBody(required = false) Map<String, Object> args,
                                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Validate Bearer token
        String token = extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid Authorization header. " +
                            "Complete the security handshake first."));
        }

        var session = sessionStore.findByToken(token);
        if (session.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid or expired session token"));
        }

        log.info("Authenticated tool call '{}' from subject '{}'",
                toolName, session.get().getVerifiedSubject());

        McpToolResult result = toolProvider.execute(toolName, args != null ? args : Map.of(), null);
        if (result.isError()) {
            return ResponseEntity.badRequest().body(Map.of("error", result.content()));
        }
        return ResponseEntity.ok(Map.of("content", result.content()));
    }

    private static String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}
