package io.jclaw.gateway

import io.jclaw.agent.AgentRuntime
import io.jclaw.agent.AgentRuntimeContext
import io.jclaw.agent.session.SessionManager
import io.jclaw.channel.*
import io.jclaw.core.model.AssistantMessage
import io.jclaw.core.model.Session
import io.jclaw.gateway.attachment.AttachmentRouter
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

    def "onMessage routes attachments through AttachmentRouter"() {
        given:
        def attachmentRouter = Mock(AttachmentRouter)
        def gatewayWithRouter = new GatewayService(
                agentRuntime, sessionManager, channelRegistry, "default", null, attachmentRouter)

        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> "telegram"
        adapter.displayName() >> "Telegram"
        adapter.sendMessage(_) >> new DeliveryResult.Success("tg_msg_1")
        channelRegistry.register(adapter)

        def session = Session.create("s1", "default:telegram:bot:user", "default")
        sessionManager.getOrCreate("default:telegram:bot:user", "default") >> session
        def response = new AssistantMessage("r1", "Got your PDF!", "model")
        agentRuntime.run(_, _) >> CompletableFuture.completedFuture(response)

        def pdfData = "pdf content".bytes
        def attachment = new ChannelMessage.Attachment("report.pdf", "application/pdf", null, pdfData)
        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user",
                "upload this", List.of(attachment), Map.of())

        when:
        gatewayWithRouter.onMessage(msg)
        Thread.sleep(100)

        then:
        1 * attachmentRouter.route({ it.filename() == "report.pdf" && it.type() == AttachmentType.PDF }, msg, null)
    }

    def "onMessage skips attachment routing when no router configured"() {
        given:
        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> "telegram"
        adapter.displayName() >> "Telegram"
        adapter.sendMessage(_) >> new DeliveryResult.Success("tg_msg_1")
        channelRegistry.register(adapter)

        def session = Session.create("s1", "default:telegram:bot:user", "default")
        sessionManager.getOrCreate(_, _) >> session
        agentRuntime.run(_, _) >> CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "m"))

        def attachment = new ChannelMessage.Attachment("file.pdf", "application/pdf", null, new byte[0])
        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user",
                "text", List.of(attachment), Map.of())

        when:
        gateway.onMessage(msg) // gateway has no attachment router

        then:
        noExceptionThrown()
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
