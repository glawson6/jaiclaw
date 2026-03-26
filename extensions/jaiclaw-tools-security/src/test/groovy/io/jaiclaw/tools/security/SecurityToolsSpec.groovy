package io.jaiclaw.tools.security

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolCatalog
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Shared
import spock.lang.Specification

class SecurityToolsSpec extends Specification {

    @Shared
    def cryptoService = new CryptoService()

    @Shared
    def sessionStore = new HandshakeSessionStore()

    @Shared
    def properties = SecurityHandshakeProperties.DEFAULT

    def "all() returns exactly 1 tool (the single handshake tool)"() {
        when:
        def tools = SecurityTools.all(cryptoService, sessionStore)

        then:
        tools.size() == 1
    }

    def "all tools are in Security section"() {
        when:
        def tools = SecurityTools.all(cryptoService, sessionStore)

        then:
        tools.every { it.definition().section() == ToolCatalog.SECTION_SECURITY }
    }

    def "all tool names start with security_"() {
        when:
        def tools = SecurityTools.all(cryptoService, sessionStore)

        then:
        tools.every { it.definition().name().startsWith("security_") }
    }

    def "the single tool is named security_handshake"() {
        when:
        def tools = SecurityTools.all(cryptoService, sessionStore)

        then:
        tools[0].definition().name() == "security_handshake"
    }

    def "registerAll adds single tool to registry"() {
        given:
        def registry = new ToolRegistry()

        when:
        SecurityTools.registerAll(registry, cryptoService, sessionStore)

        then:
        registry.size() == 1
        registry.contains("security_handshake")
    }

    def "handshakeTool factory creates a SecurityHandshakeTool"() {
        when:
        def tool = SecurityTools.handshakeTool(cryptoService, sessionStore, properties)

        then:
        tool instanceof SecurityHandshakeTool
        tool.definition().name() == "security_handshake"
    }

    def "tool has non-blank description"() {
        when:
        def tools = SecurityTools.all(cryptoService, sessionStore)

        then:
        tools.every { !it.definition().description().isBlank() }
    }

    def "tool has valid input schema"() {
        when:
        def tools = SecurityTools.all(cryptoService, sessionStore)

        then:
        tools.every { it.definition().inputSchema().contains('"type"') }
    }
}
