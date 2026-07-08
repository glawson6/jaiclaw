package io.jaiclaw.gateway.mcp.transport

import spock.lang.Specification

class StdioMcpToolProviderSpec extends Specification {

    def "returns configured server name"() {
        given:
        def provider = new StdioMcpToolProvider("fs-server", "Filesystem", "node", ["server.js"])

        expect:
        provider.getServerName() == "fs-server"
    }

    def "returns configured description"() {
        given:
        def provider = new StdioMcpToolProvider("fs-server", "Filesystem access", "node", [])

        expect:
        provider.getServerDescription() == "Filesystem access"
    }

    def "uses server name as description when description is null"() {
        given:
        def provider = new StdioMcpToolProvider("fs-server", null, "node", [])

        expect:
        provider.getServerDescription() == "fs-server"
    }

    def "returns empty tool list before start"() {
        given:
        def provider = new StdioMcpToolProvider("test", "Test", "echo", [])

        expect:
        provider.getTools().isEmpty()
    }

    def "destroy handles null process gracefully"() {
        given:
        def provider = new StdioMcpToolProvider("test", "Test", "echo", [])

        when:
        provider.destroy()

        then:
        noExceptionThrown()
    }

    def "execute returns error when process not started"() {
        given:
        def provider = new StdioMcpToolProvider("test", "Test", "echo", [])

        when:
        def result = provider.execute("tool", [:], null)

        then:
        result.isError()
        result.content().contains("Tool execution failed")
    }

    // ── Watchdog machinery ────────────────────────────────────────────────
    //
    // The full MCP handshake requires a real server that speaks JSON-RPC on
    // stdio. Rather than shipping a stub server for tests, these specs cover
    // the process-management machinery directly.

    def "default idle timeout applies when no explicit value is passed"() {
        given:
        def provider = new StdioMcpToolProvider("t", "T", "echo", [])

        expect:
        // Reflection lookup because idleTimeout is private, but this is the
        // contract worth asserting: constructor picks up the default.
        def field = StdioMcpToolProvider.class.getDeclaredField("idleTimeout")
        field.setAccessible(true)
        field.get(provider) == StdioMcpToolProvider.DEFAULT_IDLE_TIMEOUT
    }

    def "explicit zero idle timeout disables watchdog"() {
        given:
        def provider = new StdioMcpToolProvider(
                "t", "T", "echo", [], java.time.Duration.ZERO)

        expect:
        def field = StdioMcpToolProvider.class.getDeclaredField("idleTimeout")
        field.setAccessible(true)
        field.get(provider) == java.time.Duration.ZERO
    }

    def "destroy tolerates repeated calls and unstarted provider"() {
        given:
        def provider = new StdioMcpToolProvider("t", "T", "echo", [])

        when:
        provider.destroy()
        provider.destroy()
        provider.destroy()

        then:
        noExceptionThrown()
    }

    def "constructor rejects blank command"() {
        when:
        new StdioMcpToolProvider("t", "T", "", [])

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor rejects command with shell metacharacters"() {
        when:
        new StdioMcpToolProvider("t", "T", "echo; rm -rf /", [])

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor accepts a custom idle timeout"() {
        given:
        def custom = java.time.Duration.ofSeconds(30)

        when:
        def provider = new StdioMcpToolProvider("t", "T", "echo", [], custom)

        then:
        def field = StdioMcpToolProvider.class.getDeclaredField("idleTimeout")
        field.setAccessible(true)
        field.get(provider) == custom
    }

    def "terminate() SIGTERMs a live subprocess and returns after grace period"() {
        // Test the low-level machinery directly — spawn a real subprocess
        // (bash 'sleep 60' is guaranteed on POSIX build hosts) and verify
        // the terminate helper kills it. Doesn't require MCP protocol.
        given:
        def provider = new StdioMcpToolProvider("test", "Test", "echo", [])
        Process sleepy = new ProcessBuilder("bash", "-c", "sleep 60").start()
        assert sleepy.isAlive()

        when:
        def m = StdioMcpToolProvider.class.getDeclaredMethod("terminate", Process.class, String.class)
        m.setAccessible(true)
        m.invoke(provider, sleepy, "test-reason")

        then:
        !sleepy.isAlive()
    }
}
