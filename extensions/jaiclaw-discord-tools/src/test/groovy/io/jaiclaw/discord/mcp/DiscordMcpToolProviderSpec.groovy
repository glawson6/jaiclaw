package io.jaiclaw.discord.mcp

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import io.jaiclaw.discord.config.DiscordToolsProperties
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Subject

class DiscordMcpToolProviderSpec extends Specification {

    ObjectMapper objectMapper = new ObjectMapper()
    RestTemplate restTemplate = Mock()
    DiscordToolsProperties properties = new DiscordToolsProperties(true, List.of())

    @Subject
    DiscordMcpToolProvider provider = new DiscordMcpToolProvider(
            "test-bot-token", properties, restTemplate, objectMapper)

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
        def responseNode = objectMapper.readTree('{"id":"msg-123"}')
        restTemplate.postForEntity(_, _, _) >> new ResponseEntity<>(responseNode, HttpStatus.OK)

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
        def responseNode = objectMapper.readTree('[{"id":"1","content":"hi","author":{"username":"bob","id":"u1"},"timestamp":"2026-01-01T00:00:00Z"}]')
        restTemplate.exchange(_, HttpMethod.GET, _, _) >> new ResponseEntity<>(responseNode, HttpStatus.OK)

        when:
        def result = provider.execute("discord_read", [channelId: "ch-1"], null)

        then:
        !result.isError()
        result.content().contains("bob")
    }

    def "discord_delete calls DELETE endpoint"() {
        given:
        restTemplate.exchange(_, HttpMethod.DELETE, _, _) >> new ResponseEntity<>(HttpStatus.NO_CONTENT)

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
}
