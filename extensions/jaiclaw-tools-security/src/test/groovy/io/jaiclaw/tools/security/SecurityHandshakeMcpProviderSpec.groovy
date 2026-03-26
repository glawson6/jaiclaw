package io.jaiclaw.tools.security

import spock.lang.Shared
import spock.lang.Specification

class SecurityHandshakeMcpProviderSpec extends Specification {

    @Shared
    def cryptoService = new CryptoService()

    def "getServerName returns configured name"() {
        given:
        def props = propsWithApiKey("test-api-key")
        def provider = new SecurityHandshakeMcpProvider(cryptoService, new HandshakeSessionStore(), props)

        expect:
        provider.serverName == "security"
    }

    def "getTools returns 5 tools"() {
        given:
        def provider = new SecurityHandshakeMcpProvider(cryptoService, new HandshakeSessionStore(),
                propsWithApiKey("key"))

        when:
        def tools = provider.tools

        then:
        tools.size() == 5
        tools.collect { it.name() } as Set == [
            "security_server_capabilities",
            "security_server_negotiate",
            "security_server_challenge",
            "security_server_verify",
            "security_server_establish"
        ] as Set
    }

    def "capabilities returns cipher suites and bootstrap trust"() {
        given:
        def provider = new SecurityHandshakeMcpProvider(cryptoService, new HandshakeSessionStore(),
                propsWithApiKey("key"))

        when:
        def result = provider.execute("security_server_capabilities", [:], null)

        then:
        !result.isError()
        result.content().contains("ECDH-P256-AES128-GCM-SHA256")
        result.content().contains("HMAC-SHA256")
        result.content().contains("API_KEY")
        result.content().contains('"serverSide":true')
    }

    def "negotiate with valid API key succeeds"() {
        given:
        def store = new HandshakeSessionStore()
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, propsWithApiKey("my-api-key"))
        def clientKeyPair = cryptoService.generateP256KeyPair()
        def clientPublicKey = cryptoService.encodePublicKey(clientKeyPair.public)

        when:
        def result = provider.execute("security_server_negotiate", [
            clientPublicKey: clientPublicKey,
            cipherSuite: "ECDH-P256-AES128-GCM-SHA256",
            authMethod: "HMAC-SHA256",
            clientId: "test-client",
            apiKey: "my-api-key"
        ], null)

        then:
        !result.isError()
        result.content().contains("handshakeId")
        result.content().contains("serverPublicKey")
        result.content().contains("sharedSecretEstablished")
        store.size() == 1
    }

    def "negotiate with wrong API key is rejected"() {
        given:
        def provider = new SecurityHandshakeMcpProvider(cryptoService, new HandshakeSessionStore(),
                propsWithApiKey("correct-key"))
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when:
        def result = provider.execute("security_server_negotiate", [
            clientPublicKey: cryptoService.encodePublicKey(clientKeyPair.public),
            cipherSuite: "ECDH-P256-AES128-GCM-SHA256",
            authMethod: "HMAC-SHA256",
            clientId: "test-client",
            apiKey: "wrong-key"
        ], null)

        then:
        result.isError()
        result.content().contains("Bootstrap authentication failed")
    }

    def "negotiate without API key is rejected when API key bootstrap configured"() {
        given:
        def provider = new SecurityHandshakeMcpProvider(cryptoService, new HandshakeSessionStore(),
                propsWithApiKey("my-key"))
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when:
        def result = provider.execute("security_server_negotiate", [
            clientPublicKey: cryptoService.encodePublicKey(clientKeyPair.public),
            cipherSuite: "ECDH-P256-AES128-GCM-SHA256",
            authMethod: "HMAC-SHA256",
            clientId: "test-client"
        ], null)

        then:
        result.isError()
        result.content().contains("Bootstrap authentication failed")
    }

    def "negotiate without bootstrap validation succeeds when no bootstrap configured"() {
        given:
        def store = new HandshakeSessionStore()
        def props = new SecurityHandshakeProperties(HandshakeMode.LOCAL, null, null,
                null, null, null, serverProps())
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, props)
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when:
        def result = provider.execute("security_server_negotiate", [
            clientPublicKey: cryptoService.encodePublicKey(clientKeyPair.public),
            cipherSuite: "ECDH-P256-AES128-GCM-SHA256",
            authMethod: "HMAC-SHA256",
            clientId: "test-client"
        ], null)

        then:
        !result.isError()
        store.size() == 1
    }

    def "full server-side handshake flow"() {
        given:
        def store = new HandshakeSessionStore()
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, propsWithApiKey("test-key"))
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when: "negotiate"
        def negotiateResult = provider.execute("security_server_negotiate", [
            clientPublicKey: cryptoService.encodePublicKey(clientKeyPair.public),
            cipherSuite: "ECDH-P256-AES128-GCM-SHA256",
            authMethod: "HMAC-SHA256",
            clientId: "test-client",
            apiKey: "test-key"
        ], null)

        then:
        !negotiateResult.isError()

        when: "extract handshake ID"
        def matcher = negotiateResult.content() =~ /"handshakeId":\s*"([^"]+)"/
        matcher.find()
        def hsId = matcher.group(1)

        and: "challenge"
        def challengeResult = provider.execute("security_server_challenge", [
            handshakeId: hsId
        ], null)

        then:
        !challengeResult.isError()
        challengeResult.content().contains("challenge")

        when: "extract challenge and compute HMAC"
        def challengeMatcher = challengeResult.content() =~ /"challenge":\s*"([^"]+)"/
        challengeMatcher.find()
        def challenge = challengeMatcher.group(1)
        def session = store.require(hsId)
        def signature = cryptoService.hmacSign(session.sharedSecret, challenge)

        and: "verify"
        def verifyResult = provider.execute("security_server_verify", [
            handshakeId: hsId,
            method: "HMAC-SHA256",
            credential: signature
        ], null)

        then:
        !verifyResult.isError()
        verifyResult.content().contains('"verified":true')

        when: "establish"
        def establishResult = provider.execute("security_server_establish", [
            handshakeId: hsId
        ], null)

        then:
        !establishResult.isError()
        establishResult.content().contains("sessionToken")
        establishResult.content().contains("Bearer token")
        session.completed
    }

    def "challenge fails without prior key exchange"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, propsWithApiKey("key"))

        when:
        def result = provider.execute("security_server_challenge", [handshakeId: session.handshakeId], null)

        then:
        result.isError()
        result.content().contains("Key exchange has not been completed")
    }

    def "verify fails without prior challenge"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        session.sharedSecret = new byte[32]
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, propsWithApiKey("key"))

        when:
        def result = provider.execute("security_server_verify", [
            handshakeId: session.handshakeId,
            method: "HMAC-SHA256",
            credential: "some-cred"
        ], null)

        then:
        result.isError()
        result.content().contains("No challenge has been issued")
    }

    def "establish fails without identity verification"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        session.sharedSecret = new byte[32]
        session.selectedCipherSuite = "ECDH-P256-AES128-GCM-SHA256"
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, propsWithApiKey("key"))

        when:
        def result = provider.execute("security_server_establish", [handshakeId: session.handshakeId], null)

        then:
        result.isError()
        result.content().contains("Identity has not been verified")
    }

    def "verify rejects wrong HMAC"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        session.sharedSecret = new byte[32]
        new Random(42).nextBytes(session.sharedSecret)
        session.challengeNonce = "test-nonce"
        session.selectedAuthMethod = "HMAC-SHA256"
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, propsWithApiKey("key"))

        when:
        def result = provider.execute("security_server_verify", [
            handshakeId: session.handshakeId,
            method: "HMAC-SHA256",
            credential: "wrong-signature"
        ], null)

        then:
        !result.isError()
        result.content().contains('"verified":false')
    }

    def "unknown tool returns error"() {
        given:
        def provider = new SecurityHandshakeMcpProvider(cryptoService, new HandshakeSessionStore(),
                propsWithApiKey("key"))

        when:
        def result = provider.execute("unknown_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "negotiate with X25519 cipher suite works"() {
        given:
        def store = new HandshakeSessionStore()
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, propsWithApiKey("key"))
        def clientKeyPair = cryptoService.generateX25519KeyPair()

        when:
        def result = provider.execute("security_server_negotiate", [
            clientPublicKey: cryptoService.encodePublicKey(clientKeyPair.public),
            cipherSuite: "ECDH-X25519-AES256-GCM-SHA384",
            authMethod: "HMAC-SHA256",
            clientId: "x25519-client",
            apiKey: "key"
        ], null)

        then:
        !result.isError()
        result.content().contains('"algorithm":"XDH"')
    }

    def "mutual bootstrap includes server signature"() {
        given:
        def store = new HandshakeSessionStore()
        def props = new SecurityHandshakeProperties(HandshakeMode.LOCAL, null, null,
                BootstrapTrust.MUTUAL, "mutual-key", [], serverProps())
        def provider = new SecurityHandshakeMcpProvider(cryptoService, store, props)
        def clientKeyPair = cryptoService.generateP256KeyPair()

        when:
        def result = provider.execute("security_server_negotiate", [
            clientPublicKey: cryptoService.encodePublicKey(clientKeyPair.public),
            cipherSuite: "ECDH-P256-AES128-GCM-SHA256",
            authMethod: "HMAC-SHA256",
            clientId: "mutual-client",
            apiKey: "mutual-key"
        ], null)

        then:
        !result.isError()
        result.content().contains("serverSignature")
    }

    // ── helpers ──

    private SecurityHandshakeProperties propsWithApiKey(String apiKey) {
        new SecurityHandshakeProperties(HandshakeMode.LOCAL, null, null,
                BootstrapTrust.API_KEY, apiKey, null, serverProps())
    }

    private SecurityHandshakeProperties.ServerProperties serverProps() {
        new SecurityHandshakeProperties.ServerProperties(true, "security", 3600)
    }
}
