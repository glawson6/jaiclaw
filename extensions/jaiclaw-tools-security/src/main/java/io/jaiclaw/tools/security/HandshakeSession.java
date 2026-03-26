package io.jaiclaw.tools.security;

import java.security.KeyPair;
import java.time.Instant;

/**
 * Mutable state accumulated across the phases of a security handshake.
 * The shared secret is never exposed to the LLM — only the tools read/write it.
 */
public class HandshakeSession {

    private final String handshakeId;
    private final Instant createdAt;

    // Phase 1: negotiation
    private String selectedCipherSuite;
    private String selectedAuthMethod;
    private String serverNonce;
    private String clientNonce;
    private String clientId;

    // Phase 2: key exchange
    private KeyPair serverKeyPair;
    private byte[] sharedSecret;
    private String keyFingerprint;
    private String algorithm;

    // Phase 3: identity verification
    private String challengeNonce;
    private boolean identityVerified;
    private String verifiedSubject;

    // Phase 4: session establishment
    private String sessionToken;
    private boolean completed;

    public HandshakeSession(String handshakeId) {
        this.handshakeId = handshakeId;
        this.createdAt = Instant.now();
    }

    public String getHandshakeId() { return handshakeId; }
    public Instant getCreatedAt() { return createdAt; }

    public String getSelectedCipherSuite() { return selectedCipherSuite; }
    public void setSelectedCipherSuite(String selectedCipherSuite) { this.selectedCipherSuite = selectedCipherSuite; }

    public String getSelectedAuthMethod() { return selectedAuthMethod; }
    public void setSelectedAuthMethod(String selectedAuthMethod) { this.selectedAuthMethod = selectedAuthMethod; }

    public String getServerNonce() { return serverNonce; }
    public void setServerNonce(String serverNonce) { this.serverNonce = serverNonce; }

    public String getClientNonce() { return clientNonce; }
    public void setClientNonce(String clientNonce) { this.clientNonce = clientNonce; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public KeyPair getServerKeyPair() { return serverKeyPair; }
    public void setServerKeyPair(KeyPair serverKeyPair) { this.serverKeyPair = serverKeyPair; }

    public byte[] getSharedSecret() { return sharedSecret; }
    public void setSharedSecret(byte[] sharedSecret) { this.sharedSecret = sharedSecret; }

    public String getKeyFingerprint() { return keyFingerprint; }
    public void setKeyFingerprint(String keyFingerprint) { this.keyFingerprint = keyFingerprint; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getChallengeNonce() { return challengeNonce; }
    public void setChallengeNonce(String challengeNonce) { this.challengeNonce = challengeNonce; }

    public boolean isIdentityVerified() { return identityVerified; }
    public void setIdentityVerified(boolean identityVerified) { this.identityVerified = identityVerified; }

    public String getVerifiedSubject() { return verifiedSubject; }
    public void setVerifiedSubject(String verifiedSubject) { this.verifiedSubject = verifiedSubject; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
