package io.jaiclaw.channel.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.channel.*;
import io.jaiclaw.channel.process.CliProcessBridge;
import io.jaiclaw.channel.process.CliProcessConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Signal messaging channel adapter with two integration modes:
 *
 * <p><b>EMBEDDED mode</b>: Spawns and manages a signal-cli daemon process,
 * communicating via JSON-RPC 2.0 over TCP. Inbound messages arrive as
 * push notifications on the TCP stream.
 *
 * <p><b>HTTP_CLIENT mode</b>: Polls an external signal-cli-rest-api sidecar
 * via HTTP for inbound messages and sends outbound via REST.
 */
public class SignalAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SignalAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SignalConfig config;
    private final RestTemplate restTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChannelMessageHandler handler;

    // EMBEDDED mode
    private CliProcessBridge processBridge;

    // HTTP_CLIENT mode
    private Thread pollingThread;

    public SignalAdapter(SignalConfig config) {
        this(config, new RestTemplate());
    }

    public SignalAdapter(SignalConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    // Visible for testing — allows injecting a mock bridge
    SignalAdapter(SignalConfig config, RestTemplate restTemplate, CliProcessBridge processBridge) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.processBridge = processBridge;
    }

    @Override
    public String channelId() {
        return "signal";
    }

    @Override
    public String displayName() {
        return "Signal";
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;

        if (config.mode() == SignalMode.EMBEDDED) {
            startEmbedded();
        } else {
            startHttpClient();
        }

        running.set(true);
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            if (config.mode() == SignalMode.EMBEDDED) {
                return sendViaJsonRpc(message);
            } else {
                return sendViaHttp(message);
            }
        } catch (Exception e) {
            log.error("Failed to send Signal message to {}", message.peerId(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);

        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        if (processBridge != null) {
            processBridge.stop();
        }

        log.info("Signal adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // --- EMBEDDED mode ---

    private void startEmbedded() {
        try {
            var cliConfig = new CliProcessConfig(
                    config.cliCommand(),
                    List.of("-a", config.phoneNumber(), "daemon", "--tcp", "localhost:" + config.tcpPort()),
                    null,
                    config.tcpPort(),
                    30,
                    3
            );

            if (processBridge == null) {
                processBridge = new CliProcessBridge(cliConfig);
            }
            processBridge.setNotificationListener(this::handleJsonRpcNotification);
            processBridge.start();
            processBridge.startNotificationReader();

            log.info("Signal adapter started in EMBEDDED mode (phone={}, port={})",
                    config.phoneNumber(), config.tcpPort());
        } catch (IOException e) {
            log.error("Failed to start signal-cli daemon: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start signal-cli daemon", e);
        }
    }

    private void handleJsonRpcNotification(JsonNode notification) {
        try {
            // signal-cli JSON-RPC notifications have method "receive" with params containing the envelope
            String method = notification.path("method").asText();
            if (!"receive".equals(method)) return;

            JsonNode params = notification.path("params");
            JsonNode envelope = params.has("envelope") ? params.path("envelope") : params;

            processEnvelope(envelope);
        } catch (Exception e) {
            log.warn("Failed to process Signal notification: {}", e.getMessage());
        }
    }

    private DeliveryResult sendViaJsonRpc(ChannelMessage message) throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("recipient", List.of(message.peerId()));
        params.put("message", message.content());

        JsonNode result = processBridge.sendRequest("send", params);
        String timestamp = result.path("timestamp").asText(UUID.randomUUID().toString());
        return new DeliveryResult.Success(timestamp);
    }

    // --- HTTP_CLIENT mode ---

    private void startHttpClient() {
        pollingThread = Thread.ofVirtual().name("signal-poller").start(() -> {
            log.info("Signal HTTP polling started (interval={}s, url={})",
                    config.pollIntervalSeconds(), config.apiUrl());
            while (running.get() || !Thread.currentThread().isInterrupted()) {
                try {
                    pollMessages();
                    Thread.sleep(config.pollIntervalSeconds() * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Signal polling error (will retry): {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Signal HTTP polling stopped");
        });

        log.info("Signal adapter started in HTTP_CLIENT mode (phone={}, url={})",
                config.phoneNumber(), config.apiUrl());
    }

    void pollMessages() {
        String url = config.apiUrl() + "/v1/receive/" + config.phoneNumber();

        try {
            var response = restTemplate.getForEntity(url, JsonNode.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return;
            }

            JsonNode body = response.getBody();
            if (!body.isArray()) return;

            for (JsonNode envelope : body) {
                processEnvelope(envelope.path("envelope"));
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.debug("Signal API not reachable: {}", e.getMessage());
        }
    }

    private DeliveryResult sendViaHttp(ChannelMessage message) {
        String url = config.apiUrl() + "/v2/send";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message.content());
        body.put("number", config.phoneNumber());
        body.put("recipients", List.of(message.peerId()));

        try {
            var response = restTemplate.postForEntity(url, body, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String timestamp = response.getBody() != null
                        ? response.getBody().path("timestamp").asText(UUID.randomUUID().toString())
                        : UUID.randomUUID().toString();
                return new DeliveryResult.Success(timestamp);
            } else {
                return new DeliveryResult.Failure(
                        "signal_api_error",
                        "HTTP " + response.getStatusCode(),
                        true);
            }
        } catch (Exception e) {
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    // --- Common message processing ---

    void processEnvelope(JsonNode envelope) {
        if (envelope == null || envelope.isMissingNode()) return;

        // Extract sender
        String sender = envelope.path("source").asText("");
        if (sender.isEmpty()) {
            sender = envelope.path("sourceNumber").asText("");
        }
        if (sender.isEmpty()) return;

        // Check allowed-senders filter
        if (!config.isSenderAllowed(sender)) {
            log.debug("Dropping message from non-allowed Signal sender {}", sender);
            return;
        }

        // Extract data message
        JsonNode dataMessage = envelope.path("dataMessage");
        if (dataMessage.isMissingNode()) return;

        String text = dataMessage.path("message").asText("");
        long timestamp = dataMessage.path("timestamp").asLong(0);
        String messageId = timestamp > 0 ? String.valueOf(timestamp) : UUID.randomUUID().toString();

        // Skip empty messages
        if (text.isEmpty()) return;

        Map<String, Object> platformData = Map.of(
                "source", sender,
                "timestamp", timestamp
        );

        var channelMessage = ChannelMessage.inbound(
                messageId, "signal", config.phoneNumber(), sender, text, platformData);

        if (handler != null) {
            handler.onMessage(channelMessage);
        }
    }
}
