package io.jaiclaw.channel.telegram

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.gateway.WebhookDispatcher
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class TelegramAdapterPollingSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()
    static final JsonNode OK = MAPPER.readTree('{"ok":true}')
    static final JsonNode EMPTY_UPDATES = MAPPER.readTree('{"ok":true,"result":[]}')

    WebhookDispatcher webhookDispatcher = new WebhookDispatcher()

    def "polling fetches and dispatches updates"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)
        def latch = new CountDownLatch(1)
        def capturedMessages = new CopyOnWriteArrayList<ChannelMessage>()
        def callCount = new AtomicInteger(0)

        mockHttpClient.get(_) >> { String url ->
            if (url.contains("/deleteWebhook")) return OK
            if (url.contains("/getUpdates")) {
                if (callCount.incrementAndGet() == 1) {
                    return MAPPER.readTree('''{
                        "ok": true,
                        "result": [{
                            "update_id": 100,
                            "message": {
                                "message_id": 1,
                                "from": {"id": 111},
                                "chat": {"id": 222},
                                "text": "hello from polling"
                            }
                        }]
                    }''')
                }
                return EMPTY_UPDATES
            }
            return OK
        }

        def pollingHandler = { ChannelMessage msg ->
            capturedMessages.add(msg)
            latch.countDown()
        } as ChannelMessageHandler

        def adapter = new TelegramAdapter(config, webhookDispatcher, mockHttpClient)

        when:
        adapter.start(pollingHandler)
        latch.await(10, TimeUnit.SECONDS)

        then:
        capturedMessages.size() >= 1
        capturedMessages[0].content() == "hello from polling"
        capturedMessages[0].peerId() == "222"

        cleanup:
        adapter.stop()
    }

    def "polling stops when stop() is called"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)

        mockHttpClient.get(_) >> { String url ->
            if (url.contains("/deleteWebhook")) return OK
            if (url.contains("/getUpdates")) {
                Thread.sleep(500)
                return EMPTY_UPDATES
            }
            return OK
        }

        def adapter = new TelegramAdapter(config, webhookDispatcher, mockHttpClient)
        adapter.start(Mock(ChannelMessageHandler))

        when:
        Thread.sleep(200)
        adapter.stop()
        Thread.sleep(1000)

        then:
        !adapter.isRunning()
    }

    def "polling recovers from connection errors"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)
        def callCount = new AtomicInteger(0)
        def latch = new CountDownLatch(1)

        mockHttpClient.get(_) >> { String url ->
            if (url.contains("/deleteWebhook")) return OK
            if (url.contains("/getUpdates")) {
                int count = callCount.incrementAndGet()
                if (count == 1) {
                    throw new TelegramHttpException("Connection refused")
                }
                latch.countDown()
                return EMPTY_UPDATES
            }
            return OK
        }

        def adapter = new TelegramAdapter(config, webhookDispatcher, mockHttpClient)
        adapter.start(Mock(ChannelMessageHandler))

        when: "wait for adapter to recover (5s retry delay + buffer)"
        def recovered = latch.await(20, TimeUnit.SECONDS)

        then: "adapter recovered and made a second successful call"
        recovered
        callCount.get() >= 2

        cleanup:
        adapter.stop()
    }

    def "deleteWebhook is called before polling starts"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)
        def deleteWebhookCalled = new CountDownLatch(1)

        mockHttpClient.get(_) >> { String url ->
            if (url.contains("/deleteWebhook")) {
                deleteWebhookCalled.countDown()
                return OK
            }
            if (url.contains("/getUpdates")) {
                Thread.sleep(200)
                return EMPTY_UPDATES
            }
            return OK
        }

        def adapter = new TelegramAdapter(config, webhookDispatcher, mockHttpClient)

        when:
        adapter.start(Mock(ChannelMessageHandler))
        def called = deleteWebhookCalled.await(5, TimeUnit.SECONDS)

        then:
        called

        cleanup:
        adapter.stop()
    }

    def "polling handles empty results without dispatching"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)
        def handler = Mock(ChannelMessageHandler)
        def pollCount = new AtomicInteger(0)
        def latch = new CountDownLatch(2)

        mockHttpClient.get(_) >> { String url ->
            if (url.contains("/deleteWebhook")) return OK
            if (url.contains("/getUpdates")) {
                pollCount.incrementAndGet()
                latch.countDown()
                return EMPTY_UPDATES
            }
            return OK
        }

        def adapter = new TelegramAdapter(config, webhookDispatcher, mockHttpClient)

        when:
        adapter.start(handler)
        latch.await(10, TimeUnit.SECONDS)

        then: "no messages dispatched for empty results"
        0 * handler.onMessage(_)

        cleanup:
        adapter.stop()
    }

    def "polling advances offset after processing updates"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)
        def requestUrls = new CopyOnWriteArrayList<String>()
        def latch = new CountDownLatch(2)

        mockHttpClient.get(_) >> { String url ->
            if (url.contains("/deleteWebhook")) return OK
            if (url.contains("/getUpdates")) {
                requestUrls.add(url)
                latch.countDown()
                if (requestUrls.size() == 1) {
                    return MAPPER.readTree('''{
                        "ok": true,
                        "result": [{
                            "update_id": 500,
                            "message": {
                                "message_id": 1,
                                "from": {"id": 111},
                                "chat": {"id": 222},
                                "text": "test"
                            }
                        }]
                    }''')
                }
                return EMPTY_UPDATES
            }
            return OK
        }

        def adapter = new TelegramAdapter(config, webhookDispatcher, mockHttpClient)
        adapter.start(Mock(ChannelMessageHandler))

        when:
        latch.await(10, TimeUnit.SECONDS)

        then: "second request includes offset=501 (update_id 500 + 1)"
        requestUrls.size() >= 2
        requestUrls[0].contains("offset=0")
        requestUrls[1].contains("offset=501")

        cleanup:
        adapter.stop()
    }
}
