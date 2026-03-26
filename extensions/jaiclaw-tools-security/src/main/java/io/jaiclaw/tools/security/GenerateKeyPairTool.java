package io.jaiclaw.tools.security;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.security.KeyPair;
import java.util.Map;
import java.util.Set;

/**
 * Generates a cryptographic key pair for the client side of the handshake.
 * The LLM calls this tool to get a real public key to send to the server.
 *
 * <p>The private key is stored in the session store (never exposed to the LLM).
 * The public key is returned as a Base64url string for use in negotiate.
 */
public class GenerateKeyPairTool extends AbstractSecurityTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "algorithm": {
                  "type": "string",
                  "description": "Key algorithm: 'P-256' (default) or 'X25519'"
                }
              },
              "required": []
            }""";

    public GenerateKeyPairTool(CryptoService cryptoService, HandshakeSessionStore sessionStore,
                               SecurityHandshakeProperties properties) {
        super(new ToolDefinition(
                "security_generate_keypair",
                "Generate a client key pair for the security handshake. Returns the public key (Base64url) "
                        + "to use with security_negotiate_session. The private key is securely stored internally.",
                ToolCatalog.SECTION_SECURITY,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), cryptoService, sessionStore, properties);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String algorithm = optionalParam(parameters, "algorithm", "P-256");

        boolean useX25519 = "X25519".equalsIgnoreCase(algorithm);
        KeyPair keyPair = useX25519
                ? cryptoService.generateX25519KeyPair()
                : cryptoService.generateP256KeyPair();

        String publicKeyEncoded = cryptoService.encodePublicKey(keyPair.getPublic());

        // Store the key pair in a temporary session so the private key can be used
        // later for key agreement if needed (ORCHESTRATED mode)
        String keyId = "keypair-" + java.util.UUID.randomUUID();
        HandshakeSession tempSession = new HandshakeSession(keyId);
        tempSession.setServerKeyPair(keyPair); // reuse field to store client key pair
        tempSession.setAlgorithm(useX25519 ? "XDH" : "ECDH");
        sessionStore.put(keyId, tempSession);

        return new ToolResult.Success(String.format("""
                {
                  "keyId": "%s",
                  "algorithm": "%s",
                  "publicKey": "%s",
                  "instructions": "Use this publicKey as the clientPublicKey parameter when calling security_negotiate_session."
                }""",
                keyId, algorithm, publicKeyEncoded));
    }
}
