package io.jclaw.channel.sms;

import io.jclaw.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SMS/MMS channel adapter using Twilio REST API.
 * Inbound: receives Twilio webhook POST requests parsed by the gateway.
 * Outbound: sends SMS via Twilio Messages API.
 */
public class SmsAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SmsAdapter.class);
    private static final String TWILIO_API_BASE = "https://api.twilio.com/2010-04-01/Accounts/";

    private final SmsConfig config;
    private final RestTemplate restTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChannelMessageHandler handler;

    public SmsAdapter(SmsConfig config) {
        this(config, new RestTemplate());
    }

    public SmsAdapter(SmsConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    @Override
    public String channelId() {
        return "sms";
    }

    @Override
    public String displayName() {
        return "SMS";
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;
        running.set(true);
        log.info("SMS adapter started: from={}, webhook={}", config.fromNumber(), config.webhookPath());
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            String url = TWILIO_API_BASE + config.accountSid() + "/Messages.json";
            String body = "From=" + encode(config.fromNumber())
                    + "&To=" + encode(message.peerId())
                    + "&Body=" + encode(message.content());

            // Twilio expects form-encoded POST with Basic auth
            var headers = new org.springframework.http.HttpHeaders();
            headers.setBasicAuth(config.accountSid(), config.authToken());
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

            var request = new org.springframework.http.HttpEntity<>(body, headers);
            var response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String sid = String.valueOf(response.getBody().get("sid"));
                return new DeliveryResult.Success(sid);
            }
            return new DeliveryResult.Failure("sms_send_failed",
                    "HTTP " + response.getStatusCode(), true);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", message.peerId(), e.getMessage());
            return new DeliveryResult.Failure("sms_send_failed", e.getMessage(), true);
        }
    }

    /**
     * Process an inbound Twilio webhook. Called by the gateway's webhook dispatcher.
     *
     * @param params the form parameters from Twilio's webhook POST
     */
    public void processWebhook(Map<String, String> params) {
        String from = params.getOrDefault("From", "");

        if (!config.isSenderAllowed(from)) {
            log.debug("Dropping SMS from non-allowed sender {}", from);
            return;
        }

        String body = params.getOrDefault("Body", "");
        String messageSid = params.getOrDefault("MessageSid", UUID.randomUUID().toString());
        int numMedia = Integer.parseInt(params.getOrDefault("NumMedia", "0"));

        List<ChannelMessage.Attachment> attachments = new ArrayList<>();
        for (int i = 0; i < numMedia; i++) {
            String mediaUrl = params.get("MediaUrl" + i);
            String mediaType = params.getOrDefault("MediaContentType" + i, "application/octet-stream");
            if (mediaUrl != null) {
                attachments.add(new ChannelMessage.Attachment(
                        "media_" + i, mediaType, mediaUrl, null));
            }
        }

        Map<String, Object> platformData = new HashMap<>();
        platformData.put("messageSid", messageSid);
        if (params.containsKey("FromCity")) {
            platformData.put("fromCity", params.get("FromCity"));
        }

        ChannelMessage message = ChannelMessage.inbound(
                messageSid, "sms", config.fromNumber(), from,
                body, attachments, platformData);

        if (handler != null) {
            handler.onMessage(message);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        log.info("SMS adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    SmsConfig config() {
        return config;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
