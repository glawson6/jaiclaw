package io.jaiclaw.channel.telegram

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NativeTelegramPollingStrategySpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()
    static final JsonNode OK = MAPPER.readTree('{"ok":true}')
    static final JsonNode EMPTY_UPDATES = MAPPER.readTree('{"ok":true,"result":[]}')

    def "startPolling fetches and delivers updates to handler"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)
        def latch = new CountDownLatch(1)
        def receivedUpdates = new CopyOnWriteArrayList<JsonNode>()
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
                                "text": "hello from native polling"
                            }
                        }]
                    }''')
                }
                return EMPTY_UPDATES
            }
            return OK
        }

        TelegramUpdateHandler handler = { JsonNode update ->
            receivedUpdates.add(update)
            latch.countDown()
        }

        def strategy = new NativeTelegramPollingStrategy(mockHttpClient)

        when:
        strategy.startPolling(config, handler)
        latch.await(10, TimeUnit.SECONDS)

        then:
        strategy.isPolling()
        receivedUpdates.size() >= 1
        receivedUpdates[0].path("update_id").asLong() == 100

        cleanup:
        strategy.stopPolling()
    }

    def "stopPolling stops the polling loop"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)

        mockHttpClient.get(_) >> { String url ->
            if (url.contains("/deleteWebhook")) return OK
            if (url.contains("/getUpdates")) {
                Thread.sleep(200)
                return EMPTY_UPDATES
            }
            return OK
        }

        def strategy = new NativeTelegramPollingStrategy(mockHttpClient)
        strategy.startPolling(config, { JsonNode update -> })

        when:
        Thread.sleep(300)
        strategy.stopPolling()
        Thread.sleep(500)

        then:
        !strategy.isPolling()
    }

    def "polling recovers from exceptions (catches Throwable)"() {
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

        def strategy = new NativeTelegramPollingStrategy(mockHttpClient)

        when:
        strategy.startPolling(config, { JsonNode update -> })
        def recovered = latch.await(20, TimeUnit.SECONDS)

        then: "adapter recovered and made a second successful call"
        recovered
        callCount.get() >= 2

        cleanup:
        strategy.stopPolling()
    }

    def "startPolling is idempotent"() {
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

        def strategy = new NativeTelegramPollingStrategy(mockHttpClient)

        when:
        strategy.startPolling(config, { JsonNode update -> })
        strategy.startPolling(config, { JsonNode update -> })

        then:
        strategy.isPolling()

        cleanup:
        strategy.stopPolling()
    }

    def "stopPolling is idempotent"() {
        given:
        def config = new TelegramConfig("TOKEN", "", true, 1)
        def mockHttpClient = Mock(TelegramHttpClient)

        mockHttpClient.get(_) >> { String url ->
            if (url.contains("/deleteWebhook")) return OK
            return EMPTY_UPDATES
        }

        def strategy = new NativeTelegramPollingStrategy(mockHttpClient)
        strategy.startPolling(config, { JsonNode update -> })

        when:
        strategy.stopPolling()
        strategy.stopPolling()

        then:
        noExceptionThrown()
        !strategy.isPolling()
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

        def strategy = new NativeTelegramPollingStrategy(mockHttpClient)

        when:
        strategy.startPolling(config, { JsonNode update -> })
        def called = deleteWebhookCalled.await(5, TimeUnit.SECONDS)

        then:
        called

        cleanup:
        strategy.stopPolling()
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

        def strategy = new NativeTelegramPollingStrategy(mockHttpClient)
        strategy.startPolling(config, { JsonNode update -> })

        when:
        latch.await(10, TimeUnit.SECONDS)

        then: "second request includes offset=501 (update_id 500 + 1)"
        requestUrls.size() >= 2
        requestUrls[0].contains("offset=0")
        requestUrls[1].contains("offset=501")

        cleanup:
        strategy.stopPolling()
    }
}
