package io.jaiclaw.tools.security;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Map;

/**
 * Embabel GOAP agent that orchestrates the 4-phase security handshake
 * using deterministic crypto operations — no LLM calls.
 *
 * <pre>{@code
 * Calling LLM                       SecurityHandshakeTool             MCP Server
 *     │                                     │                            │
 *     ├─ security_handshake ───────────────►│                            │
 *     │  (mcpServerUrl, clientId)           │                            │
 *     │                                     │                            │
 *     │                          ┌──────────┴──────────┐                 │
 *     │                          │  Embabel GOAP Agent  │                 │
 *     │                          │  (this class)        │                 │
 *     │                          └──────────┬──────────┘                 │
 *     │                                     │                            │
 *     │                     1. negotiateCapabilities                     │
 *     │                     HandshakeRequest → ServerHello               │
 *     │                                     ├─ GET /capabilities ───────►│
 *     │                                     │◄── cipher suites ─────────┤
 *     │                                     │                            │
 *     │                     2. performKeyExchange                        │
 *     │                     ServerHello → KeyExchangeResult              │
 *     │                                     ├─ generate keypair          │
 *     │                                     ├─ POST /negotiate ─────────►│
 *     │                                     │◄── serverPublicKey ────────┤
 *     │                                     ├─ key agreement (local)     │
 *     │                                     │                            │
 *     │                     3. verifyIdentity                            │
 *     │                     KeyExchangeResult → AuthResult               │
 *     │                                     ├─ POST /challenge ─────────►│
 *     │                                     │◄── challenge nonce ────────┤
 *     │                                     ├─ HMAC sign (local)         │
 *     │                                     ├─ POST /verify ────────────►│
 *     │                                     │◄── verified ──────────────┤
 *     │                                     │                            │
 *     │                     4. establishSession (@AchievesGoal)          │
 *     │                     AuthResult → SessionEstablished              │
 *     │                                     ├─ POST /establish ─────────►│
 *     │                                     │◄── sessionToken ───────────┤
 *     │                                     │                            │
 *     │◄── { sessionToken, ... } ───────────┤                            │
 *     │                                     │                            │
 *     ├─ protected_tool(token) ─────────────────────────────────────────►│
 * }</pre>
 *
 * <p>The GOAP planner chains the actions sequentially because each action
 * requires the output of the previous one as a parameter:
 * <ol>
 *   <li>negotiateCapabilities — HandshakeRequest → ServerHello</li>
 *   <li>performKeyExchange — ServerHello → KeyExchangeResult</li>
 *   <li>verifyIdentity — KeyExchangeResult → AuthResult</li>
 *   <li>establishSession — AuthResult → SessionEstablished (goal)</li>
 * </ol>
 *
 * <p>Each action performs deterministic crypto operations directly using
 * {@link CryptoService} — no {@code context.ai().createObject()} calls.
 * The agent is still valuable because:
 * <ul>
 *   <li>Embabel's GOAP planner provides observable, traceable execution</li>
 *   <li>Each action is a discrete phase with typed input/output on the blackboard</li>
 *   <li>The blackboard records make the flow auditable</li>
 *   <li>Multiple negotiation flows can be registered as separate {@code @Agent} implementations</li>
 * </ul>
 */
@Agent(description = "Orchestrates a deterministic security handshake analogous to TLS/SSL, "
        + "using ECDH key exchange, challenge-response authentication, and JWT session tokens")
public class SecurityHandshakeAgent {

    private final CryptoService cryptoService;
    private final HandshakeSessionStore sessionStore;

    public SecurityHandshakeAgent(CryptoService cryptoService, HandshakeSessionStore sessionStore) {
        this.cryptoService = cryptoService;
        this.sessionStore = sessionStore;
    }

    @Action(description = "Negotiate capabilities — select cipher suite and auth method from client preferences")
    public ServerHello negotiateCapabilities(HandshakeRequest request, OperationContext context) {
        // Select best cipher suite: prefer X25519, fallback to P-256
        String selectedCipherSuite = selectCipherSuite(request);
        String selectedAuthMethod = selectAuthMethod(request);
        String serverNonce = cryptoService.generateNonce();

        HandshakeSession session = sessionStore.create();
        session.setSelectedCipherSuite(selectedCipherSuite);
        session.setSelectedAuthMethod(selectedAuthMethod);
        session.setClientId(request.clientId());
        session.setClientNonce(request.clientNonce());
        session.setServerNonce(serverNonce);

        return new ServerHello(
                session.getHandshakeId(),
                selectedCipherSuite,
                selectedAuthMethod,
                serverNonce,
                java.util.List.of(
                        "ECDH-X25519-AES256-GCM-SHA384",
                        "ECDH-P256-AES128-GCM-SHA256",
                        "DH-AES256-CBC-SHA256"
                )
        );
    }

    @Action(description = "Perform ECDH key exchange using the selected cipher suite")
    public KeyExchangeResult performKeyExchange(ServerHello hello, OperationContext context) {
        HandshakeSession session = sessionStore.require(hello.handshakeId());

        boolean useX25519 = hello.selectedCipherSuite().contains("X25519");
        String agreementAlgorithm = useX25519 ? "XDH" : "ECDH";

        // Generate both key pairs and perform key agreement
        KeyPair clientKeyPair = useX25519
                ? cryptoService.generateX25519KeyPair()
                : cryptoService.generateP256KeyPair();
        KeyPair serverKeyPair = useX25519
                ? cryptoService.generateX25519KeyPair()
                : cryptoService.generateP256KeyPair();

        byte[] sharedSecret = cryptoService.keyAgreement(
                serverKeyPair.getPrivate(), clientKeyPair.getPublic(), agreementAlgorithm);
        String fingerprint = cryptoService.sha256Fingerprint(sharedSecret);

        session.setServerKeyPair(serverKeyPair);
        session.setSharedSecret(sharedSecret);
        session.setKeyFingerprint(fingerprint);
        session.setAlgorithm(agreementAlgorithm);

        return new KeyExchangeResult(
                hello.handshakeId(),
                agreementAlgorithm,
                cryptoService.encodePublicKey(serverKeyPair.getPublic()),
                true,
                fingerprint
        );
    }

    @Action(description = "Verify identity via challenge-response using the shared secret")
    public AuthResult verifyIdentity(KeyExchangeResult keyExchange, OperationContext context) {
        HandshakeSession session = sessionStore.require(keyExchange.handshakeId());

        // Generate challenge, sign with shared secret, verify
        String challengeNonce = cryptoService.generateNonce();
        session.setChallengeNonce(challengeNonce);

        String signature = cryptoService.hmacSign(session.getSharedSecret(), challengeNonce);
        boolean verified = cryptoService.hmacVerify(session.getSharedSecret(), challengeNonce, signature);

        session.setIdentityVerified(verified);
        if (verified) {
            session.setVerifiedSubject(session.getClientId());
        }

        return new AuthResult(
                keyExchange.handshakeId(),
                session.getSelectedAuthMethod(),
                verified,
                verified ? session.getClientId() : null,
                verified ? "HMAC-SHA256 signature verified against challenge nonce"
                        : "HMAC-SHA256 signature verification failed"
        );
    }

    @Action(description = "Establish the secure session and generate a session token")
    @AchievesGoal(description = "Security handshake is complete with an authenticated, encrypted session")
    public SessionEstablished establishSession(AuthResult auth, OperationContext context) {
        HandshakeSession session = sessionStore.require(auth.handshakeId());

        if (!auth.verified()) {
            throw new IllegalStateException("Cannot establish session: identity not verified");
        }

        int ttlSeconds = 3600;
        String sessionToken = cryptoService.createSessionToken(
                session.getSharedSecret(),
                session.getVerifiedSubject(),
                Map.of(
                        "handshakeId", auth.handshakeId(),
                        "cipherSuite", session.getSelectedCipherSuite(),
                        "keyFingerprint", session.getKeyFingerprint()
                ),
                ttlSeconds);

        session.setSessionToken(sessionToken);
        session.setCompleted(true);

        return new SessionEstablished(
                auth.handshakeId(),
                sessionToken,
                session.getSelectedCipherSuite(),
                ttlSeconds,
                String.format("Security handshake complete for '%s' using %s key exchange.",
                        auth.subject(), session.getAlgorithm())
        );
    }

    private static String selectCipherSuite(HandshakeRequest request) {
        if (request.preferredCipherSuites() != null) {
            for (String suite : request.preferredCipherSuites()) {
                if (suite.contains("X25519") || suite.contains("P-256")) {
                    return suite;
                }
            }
        }
        return "ECDH-P256-AES128-GCM-SHA256";
    }

    private static String selectAuthMethod(HandshakeRequest request) {
        if (request.preferredAuthMethods() != null && !request.preferredAuthMethods().isEmpty()) {
            return request.preferredAuthMethods().getFirst();
        }
        return "HMAC-SHA256";
    }
}
