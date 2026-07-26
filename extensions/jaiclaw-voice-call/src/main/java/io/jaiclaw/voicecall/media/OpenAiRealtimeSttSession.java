package io.jaiclaw.voicecall.media;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * WebSocket client for OpenAI Realtime API for real-time speech-to-text transcription.
 * Connects to {@code wss://api.openai.com/v1/realtime} and handles transcription events.
 */
public class OpenAiRealtimeSttSession extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeSttSession.class);
    private static final String REALTIME_URL = "wss://api.openai.com/v1/realtime";

    private final String apiKey;
    private final String model;
    private final Consumer<TranscriptionEvent> eventHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocketSession session;
    private volatile boolean connected;
    private final ScheduledExecutorService reconnectScheduler;
    private int reconnectAttempts;

    public OpenAiRealtimeSttSession(String apiKey, String model,
                                     Consumer<TranscriptionEvent> eventHandler) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "gpt-4o-transcribe";
        this.eventHandler = eventHandler;
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "openai-realtime-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Connect to the OpenAI Realtime API.
     */
    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            String url = REALTIME_URL + "?model=" + model;

            org.springframework.web.socket.WebSocketHttpHeaders headers =
                    new org.springframework.web.socket.WebSocketHttpHeaders();
            headers.add("Authorization", "Bearer " + apiKey);
            headers.add("OpenAI-Beta", "realtime=v1");

            client.execute(this, headers, URI.create(url))
                    .thenAccept(s -> {
                        this.session = s;
                        this.connected = true;
                        this.reconnectAttempts = 0;
                        future.complete(null);
                        log.info("Connected to OpenAI Realtime STT");
                    })
                    .exceptionally(ex -> {
                        future.completeExceptionally(ex);
                        return null;
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Send audio data for transcription.
     *
     * @param audioBase64 Base64-encoded audio data
     */
    public void sendAudio(String audioBase64) {
        if (!connected || session == null) {
            log.warn("Cannot send audio: not connected to OpenAI Realtime");
            return;
        }

        try {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", "input_audio_buffer.append");
            msg.put("audio", audioBase64);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("Failed to send audio to OpenAI Realtime: {}", e.getMessage());
        }
    }

    /**
     * Commit the audio buffer and request transcription.
     */
    public void commitAudio() {
        if (!connected || session == null) return;

        try {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", "input_audio_buffer.commit");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("Failed to commit audio buffer: {}", e.getMessage());
        }
    }

    /**
     * Disconnect from the OpenAI Realtime API.
     */
    public void disconnect() {
        connected = false;
        reconnectScheduler.shutdown();
        if (session != null && session.isOpen()) {
            try { session.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String type = payload.path("type").asText();

        switch (type) {
            case "speech_started" -> eventHandler.accept(
                    new TranscriptionEvent(TranscriptionEvent.Type.SPEECH_STARTED, null, false));

            case "transcription.delta" -> {
                String delta = payload.path("delta").asText();
                eventHandler.accept(
                        new TranscriptionEvent(TranscriptionEvent.Type.TRANSCRIPTION_DELTA, delta, false));
            }

            case "transcription.completed" -> {
                String text = payload.path("text").asText();
                eventHandler.accept(
                        new TranscriptionEvent(TranscriptionEvent.Type.TRANSCRIPTION_COMPLETED, text, true));
            }

            case "error" -> {
                String error = payload.path("error").path("message").asText("Unknown error");
                log.error("OpenAI Realtime error: {}", error);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        connected = false;
        log.info("OpenAI Realtime connection closed: {}", status);

        // Reconnect with exponential backoff
        if (reconnectAttempts < 5) {
            long delay = (long) Math.pow(2, reconnectAttempts) * 1000;
            reconnectAttempts++;
            reconnectScheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
            log.info("Scheduling reconnection attempt {} in {}ms", reconnectAttempts, delay);
        }
    }

    /**
     * Transcription event from OpenAI Realtime.
     */
    public record TranscriptionEvent(
            Type type,
            String text,
            boolean isFinal
    ) {
        public enum Type {
            SPEECH_STARTED,
            TRANSCRIPTION_DELTA,
            TRANSCRIPTION_COMPLETED
        }
    }
}
