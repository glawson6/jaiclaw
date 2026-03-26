package io.jaiclaw.tools.security

import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests SecurityHandshakeMcpProvider via the typed HandshakeServerEndpoint interface.
 */
class HandshakeServerEndpointSpec extends Specification {

    @Shared
    def cryptoService = new CryptoService()

    def "capabilities returns structured response"() {
        given:
        HandshakeServerEndpoint endpoint = createEndpoint("test-key")

        when:
        def caps = endpoint.capabilities()

        then:
        caps.cipherSuites().contains("ECDH-P256-AES128-GCM-SHA256")
        caps.cipherSuites().contains("ECDH-X25519-AES256-GCM-SHA384")
        caps.authMethods().contains("HMAC-SHA256")
        caps.keyExchangeAlgorithms().contains("ECDH")
        caps.bootstrapTrust() == "API_KEY"
        caps.serverSide()
    }

    def "capabilities returns NONE when no bootstrap configured"() {
        given:
        HandshakeServerEndpoint endpoint = createEndpointNoBootstrap()

        when:
        def caps = endpoint.capabilities()

        then:
        caps.bootstrapTrust() == "NONE"
    }

    def "negotiate with valid API key returns typed response"() {
        given:
        def store = new HandshakeSessionStore()
        HandshakeServerEndpoint endpoint = createEndpoint("my-key", store)
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when:
        def response = endpoint.negotiate(new HandshakeServerEndpoint.NegotiateRequest(
                cryptoService.encodePublicKey(clientKeyPair.public),
                "ECDH-P256-AES128-GCM-SHA256",
                "HMAC-SHA256",
                "test-client",
                null, "my-key", null
        ))

        then:
        response.handshakeId() != null
        response.serverPublicKey() != null
        response.keyFingerprint() != null
        response.algorithm() == "ECDH"
        response.serverNonce() != null
        response.sharedSecretEstablished()
        response.serverSignature() == null
        store.size() == 1
    }

    def "negotiate with wrong API key throws SecurityException"() {
        given:
        HandshakeServerEndpoint endpoint = createEndpoint("correct-key")
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when:
        endpoint.negotiate(new HandshakeServerEndpoint.NegotiateRequest(
                cryptoService.encodePublicKey(clientKeyPair.public),
                "ECDH-P256-AES128-GCM-SHA256",
                "HMAC-SHA256",
                "test-client",
                null, "wrong-key", null
        ))

        then:
        def ex = thrown(SecurityException)
        ex.message.contains("Bootstrap authentication failed")
    }

    def "negotiate with X25519 uses XDH algorithm"() {
        given:
        HandshakeServerEndpoint endpoint = createEndpoint("key")
        def clientKeyPair = cryptoService.generateX25519KeyPair()

        when:
        def response = endpoint.negotiate(new HandshakeServerEndpoint.NegotiateRequest(
                cryptoService.encodePublicKey(clientKeyPair.public),
                "ECDH-X25519-AES256-GCM-SHA384",
                "HMAC-SHA256",
                "x25519-client",
                null, "key", null
        ))

        then:
        response.algorithm() == "XDH"
        response.sharedSecretEstablished()
    }

    def "negotiate with missing clientPublicKey throws"() {
        given:
        HandshakeServerEndpoint endpoint = createEndpoint("key")

        when:
        endpoint.negotiate(new HandshakeServerEndpoint.NegotiateRequest(
                null, "ECDH-P256-AES128-GCM-SHA256", "HMAC-SHA256",
                "client", null, "key", null
        ))

        then:
        thrown(IllegalArgumentException)
    }

    def "mutual bootstrap includes server signature"() {
        given:
        def store = new HandshakeSessionStore()
        def props = new SecurityHandshakeProperties(HandshakeMode.LOCAL, null, null,
                BootstrapTrust.MUTUAL, "mutual-key", [], serverProps())
        HandshakeServerEndpoint endpoint = new SecurityHandshakeMcpProvider(cryptoService, store, props)
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when:
        def response = endpoint.negotiate(new HandshakeServerEndpoint.NegotiateRequest(
                cryptoService.encodePublicKey(clientKeyPair.public),
                "ECDH-P256-AES128-GCM-SHA256",
                "HMAC-SHA256",
                "mutual-client",
                "client-nonce-123",
                "mutual-key", null
        ))

        then:
        response.serverSignature() != null
        response.serverSignature().length() > 0
    }

    def "challenge returns typed response"() {
        given:
        def store = new HandshakeSessionStore()
        HandshakeServerEndpoint endpoint = createEndpoint("key", store)
        def hsId = doNegotiate(endpoint, store)

        when:
        def response = endpoint.challenge(new HandshakeServerEndpoint.ChallengeRequest(hsId))

        then:
        response.handshakeId() == hsId
        response.challenge() != null
        response.authMethod() == "HMAC-SHA256"
    }

    def "challenge fails without prior key exchange"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        HandshakeServerEndpoint endpoint = createEndpoint("key", store)

        when:
        endpoint.challenge(new HandshakeServerEndpoint.ChallengeRequest(session.handshakeId))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Key exchange has not been completed")
    }

    def "verify returns typed response with verified true"() {
        given:
        def store = new HandshakeSessionStore()
        HandshakeServerEndpoint endpoint = createEndpoint("key", store)
        def hsId = doNegotiate(endpoint, store)

        and: "issue challenge"
        def challengeResponse = endpoint.challenge(new HandshakeServerEndpoint.ChallengeRequest(hsId))
        def session = store.require(hsId)
        def signature = cryptoService.hmacSign(session.sharedSecret, challengeResponse.challenge())

        when:
        def response = endpoint.verify(new HandshakeServerEndpoint.VerifyRequest(
                hsId, "HMAC-SHA256", signature
        ))

        then:
        response.handshakeId() == hsId
        response.authMethod() == "HMAC-SHA256"
        response.verified()
        response.subject() == "test-client"
        response.verificationDetails().contains("verified")
    }

    def "verify returns verified false for wrong HMAC"() {
        given:
        def store = new HandshakeSessionStore()
        HandshakeServerEndpoint endpoint = createEndpoint("key", store)
        def hsId = doNegotiate(endpoint, store)
        endpoint.challenge(new HandshakeServerEndpoint.ChallengeRequest(hsId))

        when:
        def response = endpoint.verify(new HandshakeServerEndpoint.VerifyRequest(
                hsId, "HMAC-SHA256", "wrong-signature"
        ))

        then:
        !response.verified()
        response.subject() == null
        response.verificationDetails().contains("mismatch")
    }

    def "verify fails without prior challenge"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        session.sharedSecret = new byte[32]
        HandshakeServerEndpoint endpoint = createEndpoint("key", store)

        when:
        endpoint.verify(new HandshakeServerEndpoint.VerifyRequest(
                session.handshakeId, "HMAC-SHA256", "cred"
        ))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("No challenge has been issued")
    }

    def "establish returns typed response with session token"() {
        given:
        def store = new HandshakeSessionStore()
        HandshakeServerEndpoint endpoint = createEndpoint("key", store)
        def hsId = doFullVerification(endpoint, store)

        when:
        def response = endpoint.establish(new HandshakeServerEndpoint.EstablishRequest(hsId, null))

        then:
        response.handshakeId() == hsId
        response.sessionToken() != null
        response.sessionToken().split('\\.').length == 3 // JWT format
        response.cipherSuite() == "ECDH-P256-AES128-GCM-SHA256"
        response.expiresInSeconds() == 3600
        response.subject() == "test-client"
        response.summary().contains("Bearer token")
    }

    def "establish with custom TTL"() {
        given:
        def store = new HandshakeSessionStore()
        HandshakeServerEndpoint endpoint = createEndpoint("key", store)
        def hsId = doFullVerification(endpoint, store)

        when:
        def response = endpoint.establish(new HandshakeServerEndpoint.EstablishRequest(hsId, 7200))

        then:
        response.expiresInSeconds() == 7200
    }

    def "establish fails without identity verification"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        session.sharedSecret = new byte[32]
        session.selectedCipherSuite = "ECDH-P256-AES128-GCM-SHA256"
        HandshakeServerEndpoint endpoint = createEndpoint("key", store)

        when:
        endpoint.establish(new HandshakeServerEndpoint.EstablishRequest(session.handshakeId, null))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Identity has not been verified")
    }

    def "full typed handshake flow"() {
        given:
        def store = new HandshakeSessionStore()
        HandshakeServerEndpoint endpoint = createEndpoint("test-key", store)
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when: "negotiate"
        def negotiateResponse = endpoint.negotiate(new HandshakeServerEndpoint.NegotiateRequest(
                cryptoService.encodePublicKey(clientKeyPair.public),
                "ECDH-P256-AES128-GCM-SHA256",
                "HMAC-SHA256",
                "flow-client",
                null, "test-key", null
        ))

        then:
        negotiateResponse.sharedSecretEstablished()

        when: "challenge"
        def challengeResponse = endpoint.challenge(
                new HandshakeServerEndpoint.ChallengeRequest(negotiateResponse.handshakeId()))

        then:
        challengeResponse.challenge() != null

        when: "sign and verify"
        def session = store.require(negotiateResponse.handshakeId())
        def sig = cryptoService.hmacSign(session.sharedSecret, challengeResponse.challenge())
        def verifyResponse = endpoint.verify(new HandshakeServerEndpoint.VerifyRequest(
                negotiateResponse.handshakeId(), "HMAC-SHA256", sig))

        then:
        verifyResponse.verified()

        when: "establish"
        def establishResponse = endpoint.establish(
                new HandshakeServerEndpoint.EstablishRequest(negotiateResponse.handshakeId(), null))

        then:
        establishResponse.sessionToken() != null
        session.completed
        store.findByToken(establishResponse.sessionToken()).isPresent()
    }

    // ── helpers ──

    private HandshakeServerEndpoint createEndpoint(String apiKey) {
        createEndpoint(apiKey, new HandshakeSessionStore())
    }

    private HandshakeServerEndpoint createEndpoint(String apiKey, HandshakeSessionStore store) {
        def props = new SecurityHandshakeProperties(HandshakeMode.LOCAL, null, null,
                BootstrapTrust.API_KEY, apiKey, null, serverProps())
        new SecurityHandshakeMcpProvider(cryptoService, store, props)
    }

    private HandshakeServerEndpoint createEndpointNoBootstrap() {
        def props = new SecurityHandshakeProperties(HandshakeMode.LOCAL, null, null,
                null, null, null, serverProps())
        new SecurityHandshakeMcpProvider(cryptoService, new HandshakeSessionStore(), props)
    }

    private SecurityHandshakeProperties.ServerProperties serverProps() {
        new SecurityHandshakeProperties.ServerProperties(true, "security", 3600)
    }

    /**
     * Performs negotiate and returns the handshakeId.
     */
    private String doNegotiate(HandshakeServerEndpoint endpoint, HandshakeSessionStore store) {
        def clientKeyPair = cryptoService.generateP256KeyPair()
        def response = endpoint.negotiate(new HandshakeServerEndpoint.NegotiateRequest(
                cryptoService.encodePublicKey(clientKeyPair.public),
                "ECDH-P256-AES128-GCM-SHA256",
                "HMAC-SHA256",
                "test-client",
                null, "key", null
        ))
        response.handshakeId()
    }

    /**
     * Performs negotiate → challenge → verify and returns the handshakeId.
     */
    private String doFullVerification(HandshakeServerEndpoint endpoint, HandshakeSessionStore store) {
        def hsId = doNegotiate(endpoint, store)
        def challengeResponse = endpoint.challenge(new HandshakeServerEndpoint.ChallengeRequest(hsId))
        def session = store.require(hsId)
        def signature = cryptoService.hmacSign(session.sharedSecret, challengeResponse.challenge())
        endpoint.verify(new HandshakeServerEndpoint.VerifyRequest(hsId, "HMAC-SHA256", signature))
        hsId
    }
}
