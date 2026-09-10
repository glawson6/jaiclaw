package io.jaiclaw.gateway.e2e

import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.TokenUsage
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.gateway.GatewayProperties
import io.jaiclaw.gateway.GatewayService
import io.jaiclaw.gateway.openai.ChatCompletionRequest
import io.jaiclaw.gateway.openai.ChatCompletionResponse
import io.jaiclaw.gateway.openai.OpenAiCompatController
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * §5.2 row 5 of the 1.2.0 plan — the OpenAI-compatible surface, plus the
 * WEBHOOK_SAFE profile contract the webhook channel depends on.
 */
class SurfacesE2ESpec extends Specification {

    GatewayService gateway = Mock()

    private static GatewayProperties props(boolean enabled,
                                           String strategy = GatewayProperties.SESSION_PER_REQUEST) {
        new GatewayProperties(true, GatewayProperties.DEFAULT_ESTOP_MESSAGE, enabled, strategy)
    }

    private static ChatCompletionRequest request(String content, boolean stream = false,
                                                 String model = "assistant") {
        new ChatCompletionRequest(model,
                [new ChatCompletionRequest.Message("user", content)], stream)
    }

    private static AssistantMessage reply(String text, int inTok = 10, int outTok = 5) {
        AssistantMessage.builder().id("m1").content(text)
                .usage(new TokenUsage(inTok, outTok, 0, 0)).build()
    }

    def "a non-streaming completion round-trips in the OpenAI shape"() {
        given:
        def controller = new OpenAiCompatController(gateway, props(true))
        gateway.resolveTenant(_) >> Optional.empty()
        gateway.handleAsync(_, "what is 2+2?") >> CompletableFuture.completedFuture(reply("4"))

        when:
        def result = controller.chatCompletions(request("what is 2+2?"), [:])

        then:
        result instanceof ResponseEntity
        result.statusCode == HttpStatus.OK

        and:
        ChatCompletionResponse body = result.body
        body.object() == "chat.completion"
        body.id().startsWith("chatcmpl-")
        body.model() == "assistant"
        body.choices()[0].message().role() == "assistant"
        body.choices()[0].message().content() == "4"
        body.choices()[0].finish_reason() == "stop"
    }

    def "token usage is reported from the runtime"() {
        given:
        def controller = new OpenAiCompatController(gateway, props(true))
        gateway.resolveTenant(_) >> Optional.empty()
        gateway.handleAsync(_, _) >> CompletableFuture.completedFuture(reply("hi", 123, 45))

        when:
        def body = controller.chatCompletions(request("hello"), [:]).body

        then:
        body.usage().prompt_tokens() == 123
        body.usage().completion_tokens() == 45
        body.usage().total_tokens() == 168
    }

    def "the endpoint 404s when disabled, without advertising that it exists"() {
        given:
        def controller = new OpenAiCompatController(gateway, props(false))

        when:
        def result = controller.chatCompletions(request("hello"), [:])

        then:
        result.statusCode == HttpStatus.NOT_FOUND
        0 * gateway.handleAsync(_, _)
    }

    def "a request with no user message is rejected"() {
        given:
        def controller = new OpenAiCompatController(gateway, props(true))
        gateway.resolveTenant(_) >> Optional.empty()

        when:
        def result = controller.chatCompletions(
                new ChatCompletionRequest("assistant",
                        [new ChatCompletionRequest.Message("system", "be nice")], false), [:])

        then:
        result.statusCode == HttpStatus.BAD_REQUEST
        0 * gateway.handleAsync(_, _)
    }

    def "the last user message is what gets answered"() {
        given:
        def controller = new OpenAiCompatController(gateway, props(true))
        gateway.resolveTenant(_) >> Optional.empty()
        def asked = null

        when:
        controller.chatCompletions(new ChatCompletionRequest("assistant", [
                new ChatCompletionRequest.Message("user", "first question"),
                new ChatCompletionRequest.Message("assistant", "first answer"),
                new ChatCompletionRequest.Message("user", "second question"),
        ], false), [:])

        then:
        1 * gateway.handleAsync(_, _) >> { String key, String content ->
            asked = content
            CompletableFuture.completedFuture(reply("ok"))
        }
        asked == "second question"
    }

    def "per-request strategy gives every call a fresh session"() {
        given:
        def controller = new OpenAiCompatController(gateway, props(true))
        gateway.resolveTenant(_) >> Optional.empty()
        def keys = []
        gateway.handleAsync(_, _) >> { String key, String c ->
            keys << key
            CompletableFuture.completedFuture(reply("ok"))
        }

        when:
        2.times { controller.chatCompletions(request("hello"), ["X-JaiClaw-User": "alice"]) }

        then: "stateless by default — the client's message list is the history"
        keys.size() == 2
        keys[0] != keys[1]
    }

    def "by-user-header strategy gives one durable session per user"() {
        given:
        def controller = new OpenAiCompatController(gateway,
                props(true, GatewayProperties.SESSION_BY_USER_HEADER))
        gateway.resolveTenant(_) >> Optional.empty()
        def keys = []
        gateway.handleAsync(_, _) >> { String key, String c ->
            keys << key
            CompletableFuture.completedFuture(reply("ok"))
        }

        when:
        2.times { controller.chatCompletions(request("hello"), ["X-JaiClaw-User": "alice"]) }
        controller.chatCompletions(request("hello"), ["X-JaiClaw-User": "bob"])

        then:
        keys[0] == keys[1]
        keys[0] == "assistant:openai:api:alice"
        keys[2] != keys[0]
    }

    def "the user header is matched case-insensitively"() {
        given:
        def controller = new OpenAiCompatController(gateway,
                props(true, GatewayProperties.SESSION_BY_USER_HEADER))
        gateway.resolveTenant(_) >> Optional.empty()
        def key = null
        gateway.handleAsync(_, _) >> { String k, String c ->
            key = k
            CompletableFuture.completedFuture(reply("ok"))
        }

        when: "HTTP header names are case-insensitive by spec"
        controller.chatCompletions(request("hello"), ["x-jaiclaw-user": "alice"])

        then:
        key == "assistant:openai:api:alice"
    }

    def "the model field selects an agent id"() {
        given:
        def controller = new OpenAiCompatController(gateway, props(true))
        gateway.resolveTenant(_) >> Optional.empty()
        gateway.handleAsync(_, _) >> CompletableFuture.completedFuture(reply("ok"))

        expect:
        controller.chatCompletions(request("hi", false, requested), [:]).body.model() == expected

        where:
        requested   | expected
        "support"   | "support"
        null        | "default"
        ""          | "default"
    }

    // ── WEBHOOK_SAFE profile ─────────────────────────────────────────────────

    def "WEBHOOK_SAFE sits above MINIMAL but below anything that writes"() {
        expect: "a webhook payload is attacker-controlled, so it must not reach shell or files"
        ToolProfile.WEBHOOK_SAFE.privilege() > ToolProfile.MINIMAL.privilege()
        ToolProfile.WEBHOOK_SAFE.privilege() < ToolProfile.CODING.privilege()
        ToolProfile.WEBHOOK_SAFE.privilege() < ToolProfile.FULL.privilege()
    }

    def "narrowest clamps a wider request down to the webhook profile"() {
        expect:
        ToolProfile.narrowest(ToolProfile.FULL, ToolProfile.WEBHOOK_SAFE) == ToolProfile.WEBHOOK_SAFE
        ToolProfile.narrowest(ToolProfile.CODING, ToolProfile.WEBHOOK_SAFE) == ToolProfile.WEBHOOK_SAFE
        ToolProfile.narrowest(ToolProfile.MINIMAL, ToolProfile.WEBHOOK_SAFE) == ToolProfile.MINIMAL
        ToolProfile.narrowest(null, ToolProfile.WEBHOOK_SAFE) == ToolProfile.WEBHOOK_SAFE
        ToolProfile.narrowest(ToolProfile.FULL, null) == ToolProfile.FULL
        ToolProfile.narrowest(null, null) == ToolProfile.NONE
    }

    def "the privilege ordering is total and strictly increasing"() {
        expect:
        [ToolProfile.NONE, ToolProfile.MINIMAL, ToolProfile.WEBHOOK_SAFE,
         ToolProfile.MESSAGING, ToolProfile.CODING, ToolProfile.FULL]
                *.privilege() == [0, 1, 2, 3, 4, 5]
    }
}
