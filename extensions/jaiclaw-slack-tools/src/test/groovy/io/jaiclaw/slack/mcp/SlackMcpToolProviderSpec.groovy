package io.jaiclaw.slack.mcp

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import io.jaiclaw.slack.config.SlackToolsProperties
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Subject

class SlackMcpToolProviderSpec extends Specification {

    ObjectMapper objectMapper = new ObjectMapper()
    RestTemplate restTemplate = Mock()
    SlackToolsProperties properties = new SlackToolsProperties(true, List.of())

    @Subject
    SlackMcpToolProvider provider = new SlackMcpToolProvider(
            "xoxb-test-token", properties, restTemplate, objectMapper)

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
        def responseNode = objectMapper.readTree('{"ok":true,"ts":"1712023032.1234"}')
        restTemplate.postForEntity(_, _, _) >> new ResponseEntity<>(responseNode, HttpStatus.OK)

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
        def responseNode = objectMapper.readTree('{"ok":true}')
        restTemplate.postForEntity(_, _, _) >> new ResponseEntity<>(responseNode, HttpStatus.OK)

        when:
        def result = provider.execute("slack_react",
                [channelId: "C123", messageId: "1712023032.1234", emoji: ":thumbsup:"], null)

        then:
        !result.isError()
        result.content().contains("thumbsup")
    }

    def "slack_read calls conversations.history"() {
        given:
        def responseNode = objectMapper.readTree('{"ok":true,"messages":[{"ts":"1","text":"hi","user":"U1"}]}')
        restTemplate.exchange(_, HttpMethod.GET, _, _) >> new ResponseEntity<>(responseNode, HttpStatus.OK)

        when:
        def result = provider.execute("slack_read", [channelId: "C123"], null)

        then:
        !result.isError()
        result.content().contains("hi")
    }

    def "slack_member_info returns user profile"() {
        given:
        def responseNode = objectMapper.readTree('''{
            "ok":true,
            "user":{"id":"U123","name":"bob","real_name":"Bob Smith",
                     "profile":{"display_name":"bobby","email":"bob@example.com"},
                     "is_bot":false,"is_admin":false,"tz":"America/New_York"}
        }''')
        restTemplate.exchange(_, HttpMethod.GET, _, _) >> new ResponseEntity<>(responseNode, HttpStatus.OK)

        when:
        def result = provider.execute("slack_member_info", [userId: "U123"], null)

        then:
        !result.isError()
        result.content().contains("Bob Smith")
    }

    def "slack API error is propagated"() {
        given:
        def responseNode = objectMapper.readTree('{"ok":false,"error":"channel_not_found"}')
        restTemplate.postForEntity(_, _, _) >> new ResponseEntity<>(responseNode, HttpStatus.OK)

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
