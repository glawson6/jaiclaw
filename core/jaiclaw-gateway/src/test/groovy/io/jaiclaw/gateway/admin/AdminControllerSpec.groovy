package io.jaiclaw.gateway.admin

import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.channel.ChannelAdapter
import io.jaiclaw.channel.ChannelRegistry
import io.jaiclaw.core.model.Session
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Specification

class AdminControllerSpec extends Specification {

    SessionManager sessionManager = new SessionManager()
    ChannelRegistry channelRegistry = new ChannelRegistry()
    ToolRegistry toolRegistry = new ToolRegistry()
    AdminController controller

    def setup() {
        controller = new AdminController(sessionManager, channelRegistry, toolRegistry)
    }

    def "listSessions returns empty when no sessions"() {
        when:
        def response = controller.listSessions()

        then:
        response.statusCode.value() == 200
        response.body.count == 0
        response.body.sessions.isEmpty()
    }

    def "listSessions returns existing sessions"() {
        given:
        sessionManager.getOrCreate("default:telegram:bot:user1", "default")
        sessionManager.getOrCreate("default:telegram:bot:user2", "default")

        when:
        def response = controller.listSessions()

        then:
        response.statusCode.value() == 200
        response.body.count == 2
    }

    def "getSession returns 404 for unknown key"() {
        when:
        def response = controller.getSession("nonexistent")

        then:
        response.statusCode.value() == 404
    }

    def "terminateSession removes session"() {
        given:
        sessionManager.getOrCreate("test-key", "default")

        when:
        def response = controller.terminateSession("test-key")

        then:
        response.statusCode.value() == 200
        response.body.status == "terminated"
        sessionManager.get("test-key").isEmpty()
    }

    def "listChannels returns registered channels"() {
        given:
        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> "telegram"
        adapter.displayName() >> "Telegram"
        adapter.isRunning() >> true
        adapter.isStateless() >> false
        channelRegistry.register(adapter)

        when:
        def response = controller.listChannels()

        then:
        response.statusCode.value() == 200
        response.body.count == 1
        response.body.channels[0].channelId == "telegram"
    }

    def "metrics returns runtime summary"() {
        given:
        sessionManager.getOrCreate("key1", "default")

        when:
        def response = controller.metrics()

        then:
        response.statusCode.value() == 200
        response.body.totalSessions == 1
        response.body.activeSessions >= 0
    }
}
