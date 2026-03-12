package io.jclaw.channel.telegram

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.jclaw.channel.ChannelMessage
import io.jclaw.channel.ChannelMessageHandler
import io.jclaw.channel.DeliveryResult
import io.jclaw.gateway.WebhookDispatcher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class TelegramAdapterSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    WebhookDispatcher webhookDispatcher = new WebhookDispatcher()
    RestTemplate mockRestTemplate = Mock(RestTemplate)

    // Webhook mode config (has webhookUrl set)
    TelegramConfig webhookConfig = new TelegramConfig("test-bot-token", "https://example.com/webhook/telegram", true)
    TelegramAdapter webhookAdapter = new TelegramAdapter(webhookConfig, webhookDispatcher, mockRestTemplate)

    def "channelId is telegram"() {
        expect:
        webhookAdapter.channelId() == "telegram"
        webhookAdapter.displayName() == "Telegram"
    }

    def "webhook mode registers webhook handler and sets running"() {
        given:
        def handler = Mock(ChannelMessageHandler)

        when:
        webhookAdapter.start(handler)

        then:
        webhookAdapter.isRunning()
        webhookDispatcher.registeredChannels().contains("telegram")
    }

    def "stop clears running state"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))

        when:
        webhookAdapter.stop()

        then:
        !webhookAdapter.isRunning()
    }

    def "webhook parses text message and dispatches to handler"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)
        def updateJson = '''
        {
            "update_id": 123456,
            "message": {
                "message_id": 789,
                "from": {"id": 111, "first_name": "Test"},
                "chat": {"id": 222, "type": "private"},
                "text": "hello bot"
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("telegram", updateJson, Map.of())

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.channelId() == "telegram" &&
            msg.peerId() == "222" &&
            msg.content() == "hello bot" &&
            msg.direction() == ChannelMessage.Direction.INBOUND
        })
    }

    def "webhook ignores sticker-only updates"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)
        def stickerUpdate = '''
        {
            "update_id": 123456,
            "message": {
                "message_id": 789,
                "from": {"id": 111},
                "chat": {"id": 222},
                "sticker": {"file_id": "sticker123"}
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("telegram", stickerUpdate, Map.of())

        then:
        response.statusCode.value() == 200
        0 * handler.onMessage(_)
    }

    def "webhook handles malformed JSON gracefully"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))

        when:
        def response = webhookDispatcher.dispatch("telegram", "not json", Map.of())

        then:
        response.statusCode.value() == 200 // Always return 200 to Telegram
    }

    def "webhook parses document message with caption"() {
        given:
        def pdfBytes = "pdf content".bytes
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)

        // Mock getFile API response
        def getFileResponse = MAPPER.readTree('''{
            "ok": true,
            "result": {"file_id": "file123", "file_path": "documents/report.pdf"}
        }''')
        mockRestTemplate.getForEntity(_, JsonNode.class) >> new ResponseEntity<>(getFileResponse, HttpStatus.OK)
        // Mock file download
        mockRestTemplate.getForObject(_, byte[].class) >> pdfBytes
        // Mock setWebhook
        mockRestTemplate.postForEntity(_, _, String.class) >> new ResponseEntity<>("ok", HttpStatus.OK)

        def updateJson = '''
        {
            "update_id": 123457,
            "message": {
                "message_id": 790,
                "from": {"id": 111},
                "chat": {"id": 222},
                "caption": "Upload this for Marcus Thompson",
                "document": {
                    "file_id": "file123",
                    "file_name": "Marcus_Thompson_Report.pdf",
                    "mime_type": "application/pdf",
                    "file_size": 1024
                }
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("telegram", updateJson, Map.of())

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.content() == "Upload this for Marcus Thompson" &&
            msg.hasAttachments() &&
            msg.attachments().size() == 1 &&
            msg.attachments()[0].name() == "Marcus_Thompson_Report.pdf" &&
            msg.attachments()[0].mimeType() == "application/pdf" &&
            msg.attachments()[0].data() == pdfBytes
        })
    }

    def "extractAttachments parses document node"() {
        given:
        def pdfBytes = "pdf bytes".bytes
        def adapter = createAdapterWithMockDownload(pdfBytes)

        def messageNode = MAPPER.readTree('''{
            "message_id": 1,
            "chat": {"id": 222},
            "document": {
                "file_id": "doc_file_id",
                "file_name": "report.pdf",
                "mime_type": "application/pdf"
            }
        }''')

        when:
        def attachments = adapter.extractAttachments(messageNode)

        then:
        attachments.size() == 1
        attachments[0].name() == "report.pdf"
        attachments[0].mimeType() == "application/pdf"
        attachments[0].data() == pdfBytes
    }

    def "extractAttachments picks largest photo"() {
        given:
        def photoBytes = "photo bytes".bytes
        def downloadedFileIds = []
        def adapter = createAdapterTrackingDownloads(photoBytes, downloadedFileIds)

        def messageNode = MAPPER.readTree('''{
            "message_id": 1,
            "chat": {"id": 222},
            "photo": [
                {"file_id": "small", "width": 90, "height": 90},
                {"file_id": "medium", "width": 320, "height": 320},
                {"file_id": "large", "width": 800, "height": 800}
            ]
        }''')

        when:
        def attachments = adapter.extractAttachments(messageNode)

        then:
        attachments.size() == 1
        attachments[0].name() == "photo.jpg"
        attachments[0].mimeType() == "image/jpeg"
        downloadedFileIds == ["large"]
    }

    def "extractAttachments handles video"() {
        given:
        def videoBytes = "video bytes".bytes
        def adapter = createAdapterWithMockDownload(videoBytes)

        def messageNode = MAPPER.readTree('''{
            "message_id": 1,
            "chat": {"id": 222},
            "video": {
                "file_id": "video_id",
                "mime_type": "video/mp4"
            }
        }''')

        when:
        def attachments = adapter.extractAttachments(messageNode)

        then:
        attachments.size() == 1
        attachments[0].name() == "video.mp4"
        attachments[0].mimeType() == "video/mp4"
    }

    def "extractAttachments handles audio with file_name"() {
        given:
        def audioBytes = "audio bytes".bytes
        def adapter = createAdapterWithMockDownload(audioBytes)

        def messageNode = MAPPER.readTree('''{
            "message_id": 1,
            "chat": {"id": 222},
            "audio": {
                "file_id": "audio_id",
                "file_name": "song.mp3",
                "mime_type": "audio/mpeg"
            }
        }''')

        when:
        def attachments = adapter.extractAttachments(messageNode)

        then:
        attachments.size() == 1
        attachments[0].name() == "song.mp3"
        attachments[0].mimeType() == "audio/mpeg"
    }

    def "extractAttachments handles voice message"() {
        given:
        def voiceBytes = "voice bytes".bytes
        def adapter = createAdapterWithMockDownload(voiceBytes)

        def messageNode = MAPPER.readTree('''{
            "message_id": 1,
            "chat": {"id": 222},
            "voice": {
                "file_id": "voice_id",
                "mime_type": "audio/ogg"
            }
        }''')

        when:
        def attachments = adapter.extractAttachments(messageNode)

        then:
        attachments.size() == 1
        attachments[0].name() == "voice.ogg"
        attachments[0].mimeType() == "audio/ogg"
    }

    def "extractAttachments returns empty list for text-only message"() {
        given:
        def adapter = new TelegramAdapter(webhookConfig, webhookDispatcher, mockRestTemplate)

        def messageNode = MAPPER.readTree('''{
            "message_id": 1,
            "chat": {"id": 222},
            "text": "just text"
        }''')

        when:
        def attachments = adapter.extractAttachments(messageNode)

        then:
        attachments.isEmpty()
    }

    def "extractAttachments returns empty when download fails"() {
        given:
        def adapter = createAdapterWithMockDownload(null)

        def messageNode = MAPPER.readTree('''{
            "message_id": 1,
            "chat": {"id": 222},
            "document": {
                "file_id": "fail_id",
                "file_name": "bad.pdf",
                "mime_type": "application/pdf"
            }
        }''')

        when:
        def attachments = adapter.extractAttachments(messageNode)

        then:
        attachments.isEmpty()
    }

    // --- Helper: creates adapter subclass with overridden downloadFile ---

    private TelegramAdapter createAdapterWithMockDownload(byte[] returnBytes) {
        new TelegramAdapter(webhookConfig, webhookDispatcher, mockRestTemplate) {
            @Override
            byte[] downloadFile(String fileId) {
                return returnBytes
            }
        }
    }

    private TelegramAdapter createAdapterTrackingDownloads(byte[] returnBytes, List<String> tracker) {
        new TelegramAdapter(webhookConfig, webhookDispatcher, mockRestTemplate) {
            @Override
            byte[] downloadFile(String fileId) {
                tracker.add(fileId)
                return returnBytes
            }
        }
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new TelegramConfig(null, null, false)

        then:
        cfg.botToken() == ""
        cfg.webhookUrl() == ""
        !cfg.enabled()
        cfg.pollingTimeoutSeconds() == 30
    }

    def "config usePolling returns true when webhookUrl is blank"() {
        expect:
        new TelegramConfig("token", "", true).usePolling()
        new TelegramConfig("token", null, true).usePolling()
        !new TelegramConfig("token", "https://example.com/webhook", true).usePolling()
    }

    def "config custom polling timeout"() {
        when:
        def cfg = new TelegramConfig("token", "", true, 60)

        then:
        cfg.pollingTimeoutSeconds() == 60
    }

    def "config negative polling timeout defaults to 30"() {
        when:
        def cfg = new TelegramConfig("token", "", true, -1)

        then:
        cfg.pollingTimeoutSeconds() == 30
    }
}
