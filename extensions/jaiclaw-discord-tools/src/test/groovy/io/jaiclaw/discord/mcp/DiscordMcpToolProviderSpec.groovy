package io.jaiclaw.discord.mcp

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.discord.config.DiscordToolsProperties
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class DiscordMcpToolProviderSpec extends Specification {

    ObjectMapper objectMapper = new ObjectMapper()
    DiscordToolsProperties properties = new DiscordToolsProperties(true, List.of())

    RestClient.Builder builder = RestClient.builder()
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build()
    RestClient restClient = builder.build()

    @Subject
    DiscordMcpToolProvider provider = new DiscordMcpToolProvider(
            "test-bot-token", properties, restClient, objectMapper)

    def "server name is discord"() {
        expect:
        provider.getServerName() == "discord"
    }

    def "provides 9 tools"() {
        expect:
        provider.getTools().size() == 9
    }

    def "tool names match expected set"() {
        when:
        def names = provider.getTools().collect { it.name() }

        then:
        names.containsAll([
                "discord_send", "discord_read", "discord_react",
                "discord_edit", "discord_delete",
                "discord_pin", "discord_unpin",
                "discord_thread_create", "discord_poll"
        ])
    }

    def "discord_send posts to channel messages endpoint"() {
        given:
        server.expect(requestTo("https://discord.com/api/v10/channels/ch-1/messages"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"id":"msg-123"}', MediaType.APPLICATION_JSON))

        when:
        def result = provider.execute("discord_send",
                [channelId: "ch-1", message: "hello"], null)

        then:
        !result.isError()
        result.content().contains("msg-123")
    }

    def "discord_send requires channelId"() {
        when:
        def result = provider.execute("discord_send", [message: "hello"], null)

        then:
        result.isError()
        result.content().contains("channelId")
    }

    def "discord_react requires emoji"() {
        when:
        def result = provider.execute("discord_react",
                [channelId: "ch-1", messageId: "msg-1"], null)

        then:
        result.isError()
        result.content().contains("emoji")
    }

    def "discord_read calls GET messages endpoint"() {
        given:
        server.expect(requestTo(startsWithChannelMessages("ch-1")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(
                        '[{"id":"1","content":"hi","author":{"username":"bob","id":"u1"},"timestamp":"2026-01-01T00:00:00Z"}]',
                        MediaType.APPLICATION_JSON))

        when:
        def result = provider.execute("discord_read", [channelId: "ch-1"], null)

        then:
        !result.isError()
        result.content().contains("bob")
    }

    def "discord_delete calls DELETE endpoint"() {
        given:
        server.expect(requestTo("https://discord.com/api/v10/channels/ch-1/messages/msg-1"))
                .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT))

        when:
        def result = provider.execute("discord_delete",
                [channelId: "ch-1", messageId: "msg-1"], null)

        then:
        !result.isError()
        result.content().contains("success")
    }

    def "unknown tool returns error"() {
        when:
        def result = provider.execute("discord_unknown", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    private static org.hamcrest.Matcher<String> startsWithChannelMessages(String channelId) {
        return org.hamcrest.Matchers.startsWith(
                "https://discord.com/api/v10/channels/" + channelId + "/messages")
    }
}
