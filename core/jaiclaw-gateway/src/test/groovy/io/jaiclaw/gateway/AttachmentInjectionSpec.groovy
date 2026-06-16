package io.jaiclaw.gateway

import io.jaiclaw.agent.AgentRuntime
import io.jaiclaw.agent.AgentRuntimeContext
import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.channel.ChannelAdapter
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelRegistry
import io.jaiclaw.channel.DeliveryResult
import io.jaiclaw.channel.chunking.PlatformLimits
import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.MediaAttachment
import io.jaiclaw.core.model.Session
import io.jaiclaw.gateway.attachment.AttachmentRouter
import io.jaiclaw.gateway.attachment.RouterResult
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Specs covering the 0.9.1 image/PDF auto-injection path and the
 * {@link RouterResult}-based AttachmentRouter SPI.
 */
class AttachmentInjectionSpec extends Specification {

    AgentRuntime agentRuntime = Mock()
    SessionManager sessionManager = Mock()
    ChannelRegistry channelRegistry = new ChannelRegistry()

    def setup() {
        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> "telegram"
        adapter.displayName() >> "Telegram"
        adapter.platformLimits() >> PlatformLimits.DEFAULT
        adapter.sendMessage(_) >> new DeliveryResult.Success("ok")
        channelRegistry.register(adapter)

        def session = Session.create("s1", "default:telegram:bot:user", "default")
        sessionManager.getOrCreate(_, _) >> session
    }

    private GatewayService gateway(AttachmentRouter router, boolean autoVision = true) {
        return GatewayService.builder()
                .agentRuntime(agentRuntime)
                .sessionManager(sessionManager)
                .channelRegistry(channelRegistry)
                .defaultAgentId("default")
                .attachmentRouter(router)
                .autoVision(autoVision)
                .build()
    }

    private static ChannelMessage messageWith(ChannelMessage.Attachment att, String text = "hi") {
        return ChannelMessage.inbound("id1", "telegram", "bot", "user",
                text, List.of(att), Map.of())
    }

    def "image attachment is auto-injected as MediaAttachment when auto-vision is on"() {
        given:
        def router = Mock(AttachmentRouter)
        router.route(_, _, _) >> RouterResult.none()
        def svc = gateway(router)
        def imageBytes = "fakeimagebytes".bytes
        def msg = messageWith(new ChannelMessage.Attachment("photo.jpg", "image/jpeg", null, imageBytes))
        List<MediaAttachment> captured = null

        when:
        svc.onMessage(msg)
        Thread.sleep(100)

        then:
        1 * agentRuntime.run(_ as String, _ as List, _ as AgentRuntimeContext) >> { String text, List media, ctx ->
            captured = media
            return CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "m"))
        }
        captured != null
        captured.size() == 1
        captured[0].mimeType() == "image/jpeg"
        captured[0].filename() == "photo.jpg"
        captured[0].isImage()
    }

    def "PDF attachment is auto-injected as MediaAttachment"() {
        given:
        def router = Mock(AttachmentRouter)
        router.route(_, _, _) >> RouterResult.none()
        def svc = gateway(router)
        def msg = messageWith(new ChannelMessage.Attachment("doc.pdf", "application/pdf", null, "pdfbytes".bytes))
        List<MediaAttachment> captured = null

        when:
        svc.onMessage(msg)
        Thread.sleep(100)

        then:
        1 * agentRuntime.run(_, _, _) >> { String text, List media, ctx ->
            captured = media
            return CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "m"))
        }
        captured.size() == 1
        captured[0].isPdf()
    }

    def "non-image / non-PDF attachment is NOT auto-injected, only routed"() {
        given:
        def router = Mock(AttachmentRouter)
        def svc = gateway(router)
        def msg = messageWith(new ChannelMessage.Attachment("notes.txt", "text/plain", null, "hello".bytes))
        List<MediaAttachment> captured = null

        when:
        svc.onMessage(msg)
        Thread.sleep(100)

        then:
        1 * router.route(_, _, _) >> RouterResult.none()
        1 * agentRuntime.run(_, _, _) >> { String text, List media, ctx ->
            captured = media
            return CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "m"))
        }
        captured.isEmpty()
    }

    def "auto-vision=false skips Media injection even for images"() {
        given:
        def router = Mock(AttachmentRouter)
        router.route(_, _, _) >> RouterResult.none()
        def svc = gateway(router, false)
        def msg = messageWith(new ChannelMessage.Attachment("photo.jpg", "image/jpeg", null, "imgbytes".bytes))
        List<MediaAttachment> captured = null

        when:
        svc.onMessage(msg)
        Thread.sleep(100)

        then:
        1 * agentRuntime.run(_, _, _) >> { String text, List media, ctx ->
            captured = media
            return CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "m"))
        }
        captured.isEmpty()
    }

    def "router annotation is prepended to the agent input"() {
        given:
        def router = Mock(AttachmentRouter)
        router.route(_, _, _) >> RouterResult.annotated("[doc summary: 12 pages]")
        def svc = gateway(router)
        def msg = messageWith(
                new ChannelMessage.Attachment("doc.pdf", "application/pdf", null, "pdf".bytes),
                "what is this?")
        String capturedText = null

        when:
        svc.onMessage(msg)
        Thread.sleep(100)

        then:
        1 * agentRuntime.run(_, _, _) >> { String text, List media, ctx ->
            capturedText = text
            return CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "m"))
        }
        capturedText == "[doc summary: 12 pages]\nwhat is this?"
    }

    def "router returning null does not crash the pipeline"() {
        given:
        def router = Mock(AttachmentRouter)
        router.route(_, _, _) >> null
        def svc = gateway(router)
        def msg = messageWith(new ChannelMessage.Attachment("photo.jpg", "image/jpeg", null, "img".bytes))

        when:
        svc.onMessage(msg)
        Thread.sleep(100)

        then:
        1 * agentRuntime.run(_, _, _) >> CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "m"))
        noExceptionThrown()
    }

    def "two image attachments produce two MediaAttachment entries in order"() {
        given:
        def router = Mock(AttachmentRouter)
        router.route(_, _, _) >> RouterResult.none()
        def svc = gateway(router)
        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user", "two pics",
                List.of(
                        new ChannelMessage.Attachment("a.jpg", "image/jpeg", null, "a".bytes),
                        new ChannelMessage.Attachment("b.png", "image/png", null, "b".bytes)),
                Map.of())
        List<MediaAttachment> captured = null

        when:
        svc.onMessage(msg)
        Thread.sleep(100)

        then:
        1 * agentRuntime.run(_, _, _) >> { String text, List media, ctx ->
            captured = media
            return CompletableFuture.completedFuture(new AssistantMessage("r1", "ok", "m"))
        }
        captured.size() == 2
        captured[0].filename() == "a.jpg"
        captured[1].filename() == "b.png"
    }
}
