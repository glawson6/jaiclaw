package io.jaiclaw.channel.sms;

import io.jaiclaw.channel.AbstractChannelAdapter;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.DeliveryResult;
import io.jaiclaw.channel.chunking.PlatformLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SMS/MMS channel adapter using Twilio REST API.
 * Inbound: receives Twilio webhook POST requests parsed by the gateway.
 * Outbound: sends SMS via Twilio Messages API.
 *
 * <p>0.8.0 P3.3: now extends {@link AbstractChannelAdapter}, which final-
 * implements the {@code running} flag, {@code start}/{@code stop}
 * lifecycle, and outbound chunking against {@link PlatformLimits#SMS}
 * (160 chars).
 */
public class SmsAdapter extends AbstractChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SmsAdapter.class);
    private static final String TWILIO_API_BASE = "https://api.twilio.com/2010-04-01/Accounts/";

    private final SmsConfig config;
    private final SmsHttpClient httpClient;

    public SmsAdapter(SmsConfig config) {
        this(config, new RestClientSmsHttpClient());
    }

    /**
     * Primary constructor — inject an {@link SmsHttpClient}. Specs mock this
     * interface directly.
     */
    public SmsAdapter(SmsConfig config, SmsHttpClient httpClient) {
        super("sms", "SMS", PlatformLimits.SMS);
        this.config = config;
        this.httpClient = httpClient;
    }

    /**
     * Convenience constructor — wraps the given {@link RestClient} in the
     * default {@link RestClientSmsHttpClient}.
     */
    public SmsAdapter(SmsConfig config, RestClient restClient) {
        this(config, new RestClientSmsHttpClient(restClient));
    }

    /**
     * Legacy constructor kept for backward compatibility with 0.9.x adopters.
     *
     * @deprecated Since 1.0.0. Migrate to {@link #SmsAdapter(SmsConfig, SmsHttpClient)}
     *     or {@link #SmsAdapter(SmsConfig, RestClient)}.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public SmsAdapter(SmsConfig config, RestTemplate restTemplate) {
        this(config, new RestClientSmsHttpClient());
    }

    @Override
    protected void doStart() {
        log.info("SMS adapter started: from={}, webhook={}", config.fromNumber(), config.webhookPath());
    }

    @Override
    protected void doStop() {
        // No platform-side resources to release; the webhook dispatcher
        // continues to own its registration.
    }

    @Override
    protected DeliveryResult doSend(ChannelMessage message) {
        try {
            String url = TWILIO_API_BASE + config.accountSid() + "/Messages.json";
            String body = "From=" + encode(config.fromNumber())
                    + "&To=" + encode(message.peerId())
                    + "&Body=" + encode(message.content());

            Map<String, Object> response = httpClient.postForm(url,
                    config.accountSid(), config.authToken(), body);
            if (response != null && response.get("sid") != null) {
                return new DeliveryResult.Success(String.valueOf(response.get("sid")));
            }
            return new DeliveryResult.Failure("sms_send_failed",
                    "Twilio response missing sid", true);
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

        dispatchInbound(message);
    }

    SmsConfig config() {
        return config;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
