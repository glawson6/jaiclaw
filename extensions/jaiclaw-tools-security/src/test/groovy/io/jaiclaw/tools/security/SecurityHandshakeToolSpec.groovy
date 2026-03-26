package io.jaiclaw.tools.security

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Shared
import spock.lang.Specification

class SecurityHandshakeToolSpec extends Specification {

    @Shared
    def cryptoService = new CryptoService()

    def ctx = new ToolContext("agent-1", "session-key", "session-id", "/tmp")

    def "LOCAL mode completes full handshake and returns session token"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore,
                SecurityHandshakeProperties.DEFAULT, null)

        when:
        def result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("sessionToken")
        content.contains("handshakeId")
        content.contains("cipherSuite")
        content.contains("ECDH-P256-AES128-GCM-SHA256")
        content.contains("expiresInSeconds")
        content.contains("Bearer token")
    }

    def "LOCAL mode creates completed session in store"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore,
                SecurityHandshakeProperties.DEFAULT, null)

        when:
        def result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Success
        sessionStore.size() == 1

        // Extract handshake ID and check session
        def matcher = (result as ToolResult.Success).content() =~ /"handshakeId":\s*"([^"]+)"/
        matcher.find()
        def hsId = matcher.group(1)
        def session = sessionStore.require(hsId)
        session.completed
        session.identityVerified
        session.sessionToken != null
        session.sharedSecret != null
    }

    def "LOCAL mode uses provided clientId"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore,
                SecurityHandshakeProperties.DEFAULT, null)

        when:
        def result = tool.execute([clientId: "my-custom-client"], ctx)

        then:
        result instanceof ToolResult.Success
        def matcher = (result as ToolResult.Success).content() =~ /"handshakeId":\s*"([^"]+)"/
        matcher.find()
        def session = sessionStore.require(matcher.group(1))
        session.clientId == "my-custom-client"
        session.verifiedSubject == "my-custom-client"
    }

    def "LOCAL mode defaults clientId to agent ID from context"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore,
                SecurityHandshakeProperties.DEFAULT, null)

        when:
        def result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Success
        def matcher = (result as ToolResult.Success).content() =~ /"handshakeId":\s*"([^"]+)"/
        matcher.find()
        def session = sessionStore.require(matcher.group(1))
        session.clientId == "agent-1"
    }

    def "session token can be found by findByToken"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore,
                SecurityHandshakeProperties.DEFAULT, null)

        when:
        def result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Success

        // Extract session token
        def tokenMatcher = (result as ToolResult.Success).content() =~ /"sessionToken":\s*"([^"]+)"/
        tokenMatcher.find()
        def sessionToken = tokenMatcher.group(1)

        // Should be findable via token
        def found = sessionStore.findByToken(sessionToken)
        found.isPresent()
        found.get().completed
    }

    def "HTTP_CLIENT mode fails without MCP server URL"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def httpProps = new SecurityHandshakeProperties(
                HandshakeMode.HTTP_CLIENT, null, null, null, null, null, null)
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore, httpProps, null)

        when:
        def result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("No MCP server URL")
    }

    def "multiple handshakes create separate sessions"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore,
                SecurityHandshakeProperties.DEFAULT, null)

        when:
        def result1 = tool.execute([clientId: "client-a"], ctx)
        def result2 = tool.execute([clientId: "client-b"], ctx)

        then:
        result1 instanceof ToolResult.Success
        result2 instanceof ToolResult.Success
        sessionStore.size() == 2

        // Extract handshake IDs
        def m1 = (result1 as ToolResult.Success).content() =~ /"handshakeId":\s*"([^"]+)"/
        def m2 = (result2 as ToolResult.Success).content() =~ /"handshakeId":\s*"([^"]+)"/
        m1.find()
        m2.find()
        m1.group(1) != m2.group(1)
    }

    def "token TTL uses server properties"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def customServer = new SecurityHandshakeProperties.ServerProperties(false, "security", 7200)
        def customProps = new SecurityHandshakeProperties(
                HandshakeMode.LOCAL, null, null, null, null, null, customServer)
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore, customProps, null)

        when:
        def result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains('"expiresInSeconds": 7200')
    }

    def "full end-to-end flow: handshake then use token with ProtectedMcpTool pattern"() {
        given:
        def sessionStore = new HandshakeSessionStore()
        def tool = new SecurityHandshakeTool(cryptoService, sessionStore,
                SecurityHandshakeProperties.DEFAULT, null)

        when: "perform handshake"
        def handshakeResult = tool.execute([clientId: "e2e-client"], ctx)

        then:
        handshakeResult instanceof ToolResult.Success

        when: "extract token and verify it's in the store"
        def tokenMatcher = (handshakeResult as ToolResult.Success).content() =~ /"sessionToken":\s*"([^"]+)"/
        tokenMatcher.find()
        def sessionToken = tokenMatcher.group(1)

        then: "session is complete and token is valid"
        def found = sessionStore.findByToken(sessionToken)
        found.isPresent()
        found.get().completed
        found.get().identityVerified
        found.get().verifiedSubject == "e2e-client"
        found.get().selectedCipherSuite == "ECDH-P256-AES128-GCM-SHA256"
    }
}
