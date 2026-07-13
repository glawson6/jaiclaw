package io.jaiclaw.subscription.telegram

import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError

class TelegramGroupManagerSpec extends Specification {

    RestClient.Builder builder = RestClient.builder()
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build()
    RestClient restClient = builder.build()
    TelegramGroupManager manager = new TelegramGroupManager("test-bot-token", restClient)

    def "createInviteLink returns invite URL on success"() {
        given:
        server.expect(requestTo("https://api.telegram.org/bottest-bot-token/createChatInviteLink"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"ok":true,"result":{"invite_link":"https://t.me/+abc123"}}',
                        MediaType.APPLICATION_JSON))

        when:
        def link = manager.createInviteLink("-100123")

        then:
        link == "https://t.me/+abc123"
    }

    def "createInviteLink returns null on API failure"() {
        given:
        server.expect(requestTo("https://api.telegram.org/bottest-bot-token/createChatInviteLink"))
                .andRespond(withServerError())

        when:
        def link = manager.createInviteLink("-100123")

        then:
        link == null
    }

    def "removeUser bans then unbans"() {
        given:
        server.expect(requestTo("https://api.telegram.org/bottest-bot-token/banChatMember"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"ok":true}', MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.telegram.org/bottest-bot-token/unbanChatMember"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"ok":true}', MediaType.APPLICATION_JSON))

        when:
        def result = manager.removeUser("-100123", "456")

        then:
        result == true
    }

    def "isMember returns true for member status"() {
        given:
        server.expect(requestTo("https://api.telegram.org/bottest-bot-token/getChatMember"))
                .andRespond(withSuccess('{"ok":true,"result":{"status":"member"}}',
                        MediaType.APPLICATION_JSON))

        expect:
        manager.isMember("-100123", "456") == true
    }

    def "isMember returns false for left/kicked status"() {
        given:
        server.expect(requestTo("https://api.telegram.org/bottest-bot-token/getChatMember"))
                .andRespond(withSuccess('{"ok":true,"result":{"status":"left"}}',
                        MediaType.APPLICATION_JSON))

        expect:
        manager.isMember("-100123", "456") == false
    }

    def "sendMessage calls sendMessage API"() {
        given:
        server.expect(requestTo("https://api.telegram.org/bottest-bot-token/sendMessage"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"ok":true}', MediaType.APPLICATION_JSON))

        when:
        def result = manager.sendMessage("-100123", "Hello")

        then:
        result == true
    }
}
