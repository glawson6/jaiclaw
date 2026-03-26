package io.jaiclaw.subscription.telegram

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class TelegramGroupManagerSpec extends Specification {

    RestTemplate restTemplate = Mock()
    TelegramGroupManager manager = new TelegramGroupManager("test-bot-token", restTemplate)
    ObjectMapper mapper = new ObjectMapper()

    def "createInviteLink returns invite URL on success"() {
        given:
        def responseBody = mapper.readTree('{"ok":true,"result":{"invite_link":"https://t.me/+abc123"}}')

        when:
        def link = manager.createInviteLink("-100123")

        then:
        1 * restTemplate.postForEntity(
                { it.contains("createChatInviteLink") },
                { it["chat_id"] == "-100123" && it["member_limit"] == 1 },
                JsonNode
        ) >> new ResponseEntity(responseBody, HttpStatus.OK)

        link == "https://t.me/+abc123"
    }

    def "createInviteLink returns null on API failure"() {
        given:
        restTemplate.postForEntity(_, _, _) >> { throw new RuntimeException("API error") }

        when:
        def link = manager.createInviteLink("-100123")

        then:
        link == null
    }

    def "removeUser bans then unbans"() {
        when:
        def result = manager.removeUser("-100123", "456")

        then:
        1 * restTemplate.postForEntity(
                { it.contains("banChatMember") },
                { it["chat_id"] == "-100123" && it["user_id"] == 456L },
                JsonNode
        ) >> new ResponseEntity(mapper.readTree('{"ok":true}'), HttpStatus.OK)

        then:
        1 * restTemplate.postForEntity(
                { it.contains("unbanChatMember") },
                { it["chat_id"] == "-100123" && it["user_id"] == 456L },
                JsonNode
        ) >> new ResponseEntity(mapper.readTree('{"ok":true}'), HttpStatus.OK)

        result == true
    }

    def "isMember returns true for member status"() {
        given:
        def responseBody = mapper.readTree('{"ok":true,"result":{"status":"member"}}')
        restTemplate.postForEntity(
                { it.contains("getChatMember") }, _, JsonNode
        ) >> new ResponseEntity(responseBody, HttpStatus.OK)

        expect:
        manager.isMember("-100123", "456") == true
    }

    def "isMember returns false for left/kicked status"() {
        given:
        def responseBody = mapper.readTree('{"ok":true,"result":{"status":"left"}}')
        restTemplate.postForEntity(
                { it.contains("getChatMember") }, _, JsonNode
        ) >> new ResponseEntity(responseBody, HttpStatus.OK)

        expect:
        manager.isMember("-100123", "456") == false
    }

    def "sendMessage calls sendMessage API"() {
        given:
        restTemplate.postForEntity(_, _, _) >> new ResponseEntity(
                mapper.readTree('{"ok":true}'), HttpStatus.OK)

        when:
        def result = manager.sendMessage("-100123", "Hello")

        then:
        1 * restTemplate.postForEntity(
                { it.contains("sendMessage") },
                { it["chat_id"] == "-100123" && it["text"] == "Hello" },
                JsonNode
        ) >> new ResponseEntity(mapper.readTree('{"ok":true}'), HttpStatus.OK)

        result == true
    }
}
