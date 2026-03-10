package io.jclaw.gateway

import io.jclaw.agent.AgentRuntime
import io.jclaw.agent.AgentRuntimeContext
import io.jclaw.agent.session.SessionManager
import io.jclaw.channel.*
import io.jclaw.core.model.AssistantMessage
import io.jclaw.core.model.Session
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class GatewayServiceSpec extends Specification {

    AgentRuntime agentRuntime = Mock()
    SessionManager sessionManager = Mock()
    ChannelRegistry channelRegistry = new ChannelRegistry()
    GatewayService gateway

    def setup() {
        gateway = new GatewayService(agentRuntime, sessionManager, channelRegistry, "default")
    }

    def "onMessage routes inbound to agent and delivers response"() {
        given:
        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> "telegram"
        adapter.displayName() >> "Telegram"
        adapter.sendMessage(_) >> new DeliveryResult.Success("tg_msg_1")
        channelRegistry.register(adapter)

        def response = new AssistantMessage("resp1", "Hello back!", "gpt-4o")
        def session = Session.create("s1", "default:telegram:bot:user", "default")
        sessionManager.getOrCreate("default:telegram:bot:user", "default") >> session
        agentRuntime.run("hello", _ as AgentRuntimeContext) >> CompletableFuture.completedFuture(response)

        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user", "hello", Map.of())

        when:
        gateway.onMessage(msg)
        Thread.sleep(100) // allow async completion

        then:
        1 * adapter.sendMessage({ it.content() == "Hello back!" && it.direction() == ChannelMessage.Direction.OUTBOUND })
    }

    def "handleSync returns agent response directly"() {
        given:
        def session = Session.create("s1", "default:api:acct:peer", "default")
        sessionManager.getOrCreate("default:api:acct:peer", "default") >> session
        def response = new AssistantMessage("r1", "sync response", "model")
        agentRuntime.run("hi", _ as AgentRuntimeContext) >> CompletableFuture.completedFuture(response)

        when:
        def result = gateway.handleSync("api", "acct", "peer", "hi")

        then:
        result.content() == "sync response"
    }

    def "handleAsync returns future"() {
        given:
        def session = Session.create("s1", "key", "default")
        sessionManager.getOrCreate("key", "default") >> session
        def response = new AssistantMessage("r1", "async", "model")
        agentRuntime.run("msg", _ as AgentRuntimeContext) >> CompletableFuture.completedFuture(response)

        when:
        def future = gateway.handleAsync("key", "msg")

        then:
        future.join().content() == "async"
    }

    def "start and stop delegate to channel registry"() {
        given:
        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> "test"
        adapter.displayName() >> "Test"
        adapter.isRunning() >> true
        channelRegistry.register(adapter)

        when:
        gateway.start()

        then:
        1 * adapter.start(gateway)

        when:
        gateway.stop()

        then:
        1 * adapter.stop()
    }
}
