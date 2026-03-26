package io.jaiclaw.channel.teams

import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.gateway.WebhookDispatcher
import spock.lang.Specification

class TeamsAdapterSpec extends Specification {

    WebhookDispatcher webhookDispatcher = new WebhookDispatcher()

    TeamsConfig config = new TeamsConfig("test-app-id", "test-app-secret", true, "", true, Set.of())
    TeamsAdapter adapter = new TeamsAdapter(config, webhookDispatcher)

    def "channelId is teams"() {
        expect:
        adapter.channelId() == "teams"
        adapter.displayName() == "Microsoft Teams"
    }

    def "start registers webhook and sets running"() {
        given:
        def handler = Mock(ChannelMessageHandler)

        when:
        adapter.start(handler)

        then:
        adapter.isRunning()
        webhookDispatcher.registeredChannels().contains("teams")
    }

    def "stop clears running state"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))

        when:
        adapter.stop()

        then:
        !adapter.isRunning()
    }

    def "webhook parses message activity and dispatches to handler"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)
        def messagePayload = '''
        {
            "type": "message",
            "id": "activity-123",
            "text": "hello from teams",
            "serviceUrl": "https://smba.trafficmanager.net/teams/",
            "from": {
                "id": "user-id-1",
                "name": "Test User",
                "role": "user",
                "aadObjectId": "aad-object-1"
            },
            "conversation": {
                "id": "conv-123",
                "tenantId": "tenant-abc"
            },
            "channelData": {
                "tenant": {
                    "id": "tenant-abc"
                }
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("teams", messagePayload, Map.of())

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.channelId() == "teams" &&
            msg.peerId() == "conv-123" &&
            msg.content() == "hello from teams" &&
            msg.accountId() == "tenant-abc" &&
            msg.platformData().get("activityId") == "activity-123" &&
            msg.platformData().get("serviceUrl") == "https://smba.trafficmanager.net/teams/" &&
            msg.platformData().get("fromName") == "Test User"
        })
    }

    def "webhook ignores bot's own messages"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)
        def botMessage = '''
        {
            "type": "message",
            "id": "activity-bot",
            "text": "bot says hi",
            "serviceUrl": "https://smba.trafficmanager.net/teams/",
            "from": {
                "id": "bot-id",
                "role": "bot",
                "aadObjectId": ""
            },
            "conversation": {
                "id": "conv-123",
                "tenantId": "tenant-abc"
            },
            "channelData": {
                "tenant": { "id": "tenant-abc" }
            }
        }
        '''

        when:
        webhookDispatcher.dispatch("teams", botMessage, Map.of())

        then:
        0 * handler.onMessage(_)
    }

    def "webhook filters by allowedSenderIds"() {
        given:
        def restrictedConfig = new TeamsConfig("app-id", "secret", true, "", true, Set.of("allowed-aad"))
        def restrictedAdapter = new TeamsAdapter(restrictedConfig, webhookDispatcher)
        def handler = Mock(ChannelMessageHandler)
        restrictedAdapter.start(handler)

        def blockedMessage = '''
        {
            "type": "message",
            "id": "activity-blocked",
            "text": "should be blocked",
            "serviceUrl": "https://smba.trafficmanager.net/teams/",
            "from": {
                "id": "user-1",
                "role": "user",
                "aadObjectId": "not-allowed-aad"
            },
            "conversation": {
                "id": "conv-1",
                "tenantId": "tenant-1"
            },
            "channelData": {
                "tenant": { "id": "tenant-1" }
            }
        }
        '''

        when:
        webhookDispatcher.dispatch("teams", blockedMessage, Map.of())

        then:
        0 * handler.onMessage(_)
    }

    def "webhook ignores messages with empty text"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)
        def emptyMessage = '''
        {
            "type": "message",
            "id": "activity-empty",
            "text": "",
            "serviceUrl": "https://smba.trafficmanager.net/teams/",
            "from": {
                "id": "user-1",
                "role": "user",
                "aadObjectId": "aad-1"
            },
            "conversation": {
                "id": "conv-1",
                "tenantId": "tenant-1"
            },
            "channelData": {
                "tenant": { "id": "tenant-1" }
            }
        }
        '''

        when:
        webhookDispatcher.dispatch("teams", emptyMessage, Map.of())

        then:
        0 * handler.onMessage(_)
    }

    def "webhook handles conversationUpdate activity"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)
        def updatePayload = '''
        {
            "type": "conversationUpdate",
            "id": "activity-update",
            "serviceUrl": "https://smba.trafficmanager.net/teams/",
            "conversation": {
                "id": "conv-1",
                "tenantId": "tenant-1"
            },
            "membersAdded": [
                { "id": "bot-id", "name": "TestBot" }
            ],
            "channelData": {
                "tenant": { "id": "tenant-1" }
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("teams", updatePayload, Map.of())

        then:
        response.statusCode.value() == 200
        0 * handler.onMessage(_)
    }

    def "webhook handles invoke activity with 200 status"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)
        def invokePayload = '''
        {
            "type": "invoke",
            "id": "invoke-1",
            "serviceUrl": "https://smba.trafficmanager.net/teams/",
            "conversation": {
                "id": "conv-1",
                "tenantId": "tenant-1"
            },
            "channelData": {
                "tenant": { "id": "tenant-1" }
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("teams", invokePayload, Map.of())

        then:
        response.statusCode.value() == 200
        response.body.contains("200")
    }

    def "webhook handles malformed JSON gracefully"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        when:
        def response = webhookDispatcher.dispatch("teams", "not valid json {{{", Map.of())

        then:
        response.statusCode.value() == 200
        0 * handler.onMessage(_)
    }

    def "webhook strips bot mention from text"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)
        def mentionMessage = '''
        {
            "type": "message",
            "id": "activity-mention",
            "text": "<at>TestBot</at> what is the weather?",
            "serviceUrl": "https://smba.trafficmanager.net/teams/",
            "from": {
                "id": "user-1",
                "role": "user",
                "aadObjectId": "aad-1"
            },
            "conversation": {
                "id": "conv-1",
                "tenantId": "tenant-1"
            },
            "channelData": {
                "tenant": { "id": "tenant-1" }
            }
        }
        '''

        when:
        webhookDispatcher.dispatch("teams", mentionMessage, Map.of())

        then:
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.content() == "what is the weather?"
        })
    }

    def "serviceUrl is cached from inbound activity"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)
        def messagePayload = '''
        {
            "type": "message",
            "id": "activity-1",
            "text": "hello",
            "serviceUrl": "https://smba.trafficmanager.net/teams/",
            "from": {
                "id": "user-1",
                "role": "user",
                "aadObjectId": "aad-1"
            },
            "conversation": {
                "id": "conv-cache-test",
                "tenantId": "tenant-1"
            },
            "channelData": {
                "tenant": { "id": "tenant-1" }
            }
        }
        '''

        when:
        webhookDispatcher.dispatch("teams", messagePayload, Map.of())

        then:
        // The adapter should have cached the serviceUrl for conv-cache-test
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.peerId() == "conv-cache-test" &&
            msg.platformData().get("serviceUrl") == "https://smba.trafficmanager.net/teams/"
        })
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new TeamsConfig(null, null, false)

        then:
        cfg.appId() == ""
        cfg.appSecret() == ""
        cfg.tenantId() == ""
        cfg.allowedSenderIds() == Set.of()
    }

    def "isSenderAllowed returns true when allowlist is empty"() {
        expect:
        new TeamsConfig("id", "secret", true).isSenderAllowed("anyone")
    }

    def "isSenderAllowed filters when allowlist is set"() {
        given:
        def cfg = new TeamsConfig("id", "secret", true, "", false, Set.of("allowed-1"))

        expect:
        cfg.isSenderAllowed("allowed-1")
        !cfg.isSenderAllowed("not-allowed")
    }
}
