package io.jaiclaw.gateway

import io.jaiclaw.agent.AgentRuntime
import io.jaiclaw.agent.AgentRuntimeContext
import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.channel.ChannelAdapter
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelRegistry
import io.jaiclaw.channel.DeliveryResult
import io.jaiclaw.channel.chunking.PlatformLimits
import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.hook.event.MessageReceivedEvent
import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.Session
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Plan §5 task 1.1 — verifies the gateway funnel fires {@link MessageReceivedEvent}
 * on every inbound message, regardless of source channel adapter. One funnel = one
 * place to maintain.
 */
class GatewayServiceMessageEventSpec extends Specification {

    AgentRuntime agentRuntime = Mock()
    SessionManager sessionManager = Mock()
    ChannelRegistry channelRegistry = new ChannelRegistry()
    AgentHookDispatcher hooks = Mock()
    GatewayService gateway

    def setup() {
        gateway = new GatewayService(agentRuntime, sessionManager, channelRegistry, "default")
        gateway.setHookDispatcher(hooks)

        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> "telegram"
        adapter.displayName() >> "Telegram"
        adapter.platformLimits() >> PlatformLimits.DEFAULT
        adapter.sendMessage(_) >> new DeliveryResult.Success("ok")
        channelRegistry.register(adapter)

        def session = Session.create("s1", "default:telegram:bot:user", "default", null)
        sessionManager.getOrCreate(_, _) >> session
        agentRuntime.run(_, _ as AgentRuntimeContext) >>
                CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "model"))
    }

    def "onMessage fires MessageReceivedEvent with the channel + peer details"() {
        given:
        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user", "hello", Map.of())

        when:
        gateway.onMessage(msg)

        then:
        1 * hooks.fireVoid({ MessageReceivedEvent e ->
            e.channelId() == "telegram" &&
                e.accountId() == "bot" &&
                e.peerId() == "user" &&
                e.content() == "hello" &&
                e.sessionKey() == "default:telegram:bot:user" &&
                e.agentId() == "default"
        })
    }

    def "onMessage with no hook dispatcher set fires no events and does not throw"() {
        given:
        GatewayService noHooks = new GatewayService(agentRuntime, sessionManager, channelRegistry, "default")
        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user", "hello", Map.of())

        when:
        noHooks.onMessage(msg)

        then:
        notThrown(Exception)
    }

    def "dispatcher throwing does not break the inbound pipeline"() {
        given:
        AgentHookDispatcher boom = Mock()
        boom.fireVoid(_) >> { throw new RuntimeException("hook plugin crashed") }
        gateway.setHookDispatcher(boom)
        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user", "hello", Map.of())

        when:
        gateway.onMessage(msg)

        then:
        notThrown(Exception)
    }
}
