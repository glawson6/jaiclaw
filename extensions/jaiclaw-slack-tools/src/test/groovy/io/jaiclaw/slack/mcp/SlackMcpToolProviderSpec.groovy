package io.jaiclaw.slack.mcp

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.slack.config.SlackToolsProperties
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class SlackMcpToolProviderSpec extends Specification {

    ObjectMapper objectMapper = new ObjectMapper()
    SlackToolsProperties properties = new SlackToolsProperties(true, List.of())

    RestClient.Builder builder = RestClient.builder()
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build()
    RestClient restClient = builder.build()

    @Subject
    SlackMcpToolProvider provider = new SlackMcpToolProvider(
            "xoxb-test-token", properties, restClient, objectMapper)

    def "server name is slack"() {
        expect:
        provider.getServerName() == "slack"
    }

    def "provides 10 tools"() {
        expect:
        provider.getTools().size() == 10
    }

    def "tool names match expected set"() {
        when:
        def names = provider.getTools().collect { it.name() }

        then:
        names.containsAll([
                "slack_send", "slack_read", "slack_react",
                "slack_edit", "slack_delete",
                "slack_pin", "slack_unpin", "slack_list_pins",
                "slack_member_info", "slack_emoji_list"
        ])
    }

    def "slack_send posts to chat.postMessage"() {
        given:
        server.expect(requestTo("https://slack.com/api/chat.postMessage"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"ok":true,"ts":"1712023032.1234"}', MediaType.APPLICATION_JSON))

        when:
        def result = provider.execute("slack_send",
                [channelId: "C123", content: "hello"], null)

        then:
        !result.isError()
        result.content().contains("1712023032.1234")
    }

    def "slack_send requires channelId"() {
        when:
        def result = provider.execute("slack_send", [content: "hello"], null)

        then:
        result.isError()
        result.content().contains("channelId")
    }

    def "slack_react strips colon-wrapped emoji names"() {
        given:
        server.expect(requestTo("https://slack.com/api/reactions.add"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"ok":true}', MediaType.APPLICATION_JSON))

        when:
        def result = provider.execute("slack_react",
                [channelId: "C123", messageId: "1712023032.1234", emoji: ":thumbsup:"], null)

        then:
        !result.isError()
        result.content().contains("thumbsup")
    }

    def "slack_read calls conversations.history"() {
        given:
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://slack.com/api/conversations.history")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess('{"ok":true,"messages":[{"ts":"1","text":"hi","user":"U1"}]}', MediaType.APPLICATION_JSON))

        when:
        def result = provider.execute("slack_read", [channelId: "C123"], null)

        then:
        !result.isError()
        result.content().contains("hi")
    }

    def "slack_member_info returns user profile"() {
        given:
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://slack.com/api/users.info")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess('''{
                    "ok":true,
                    "user":{"id":"U123","name":"bob","real_name":"Bob Smith",
                             "profile":{"display_name":"bobby","email":"bob@example.com"},
                             "is_bot":false,"is_admin":false,"tz":"America/New_York"}
                }''', MediaType.APPLICATION_JSON))

        when:
        def result = provider.execute("slack_member_info", [userId: "U123"], null)

        then:
        !result.isError()
        result.content().contains("Bob Smith")
    }

    def "slack API error is propagated"() {
        given:
        server.expect(requestTo("https://slack.com/api/chat.postMessage"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"ok":false,"error":"channel_not_found"}', MediaType.APPLICATION_JSON))

        when:
        def result = provider.execute("slack_send",
                [channelId: "C999", content: "hello"], null)

        then:
        result.isError()
        result.content().contains("channel_not_found")
    }

    def "unknown tool returns error"() {
        when:
        def result = provider.execute("slack_unknown", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }
}
