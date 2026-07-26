package io.jaiclaw.voicecall.media;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.jaiclaw.voicecall.config.VoiceCallProperties;
import io.jaiclaw.voicecall.manager.CallManager;
import io.jaiclaw.voicecall.model.CallRecord;
import io.jaiclaw.voicecall.model.NormalizedEvent;
import io.jaiclaw.voicecall.model.EndReason;
import io.jaiclaw.voicecall.telephony.TelephonyProvider;
import io.jaiclaw.voicecall.telephony.twilio.TwilioTelephonyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for bidirectional Twilio media streams.
 * Receives mu-law audio from Twilio and can send audio back for TTS playback.
 */
public class MediaStreamHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MediaStreamHandler.class);

    private final VoiceCallProperties properties;
    private final CallManager callManager;
    private final TelephonyProvider telephonyProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Session management: streamSid -> session info
    private final ConcurrentHashMap<String, MediaSession> sessions = new ConcurrentHashMap<>();
    // WebSocket session ID -> streamSid mapping
    private final ConcurrentHashMap<String, String> sessionStreamMap = new ConcurrentHashMap<>();

    public MediaStreamHandler(VoiceCallProperties properties,
                              CallManager callManager,
                              TelephonyProvider telephonyProvider) {
        this.properties = properties;
        this.callManager = callManager;
        this.telephonyProvider = telephonyProvider;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("Media stream WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String event = payload.path("event").asText();

        switch (event) {
            case "connected" -> handleConnected(session, payload);
            case "start" -> handleStart(session, payload);
            case "media" -> handleMedia(session, payload);
            case "stop" -> handleStop(session, payload);
            case "mark" -> handleMark(session, payload);
            default -> log.debug("Unknown media stream event: {}", event);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String streamSid = sessionStreamMap.remove(session.getId());
        if (streamSid != null) {
            MediaSession mediaSession = sessions.remove(streamSid);
            if (mediaSession != null) {
                log.info("Media stream closed: streamSid={}, callId={}", streamSid, mediaSession.callId);
            }
        }
        log.debug("Media stream WebSocket disconnected: {} (status={})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Media stream transport error: {}", exception.getMessage());
    }

    /**
     * Send audio data to a specific stream.
     *
     * @param streamSid the stream identifier
     * @param audioBase64 Base64-encoded mu-law audio
     */
    public void sendAudio(String streamSid, String audioBase64) {
        MediaSession mediaSession = sessions.get(streamSid);
        if (mediaSession == null || mediaSession.wsSession == null) {
            log.warn("Cannot send audio to stream {}: session not found", streamSid);
            return;
        }

        try {
            ObjectNode mediaMsg = objectMapper.createObjectNode();
            mediaMsg.put("event", "media");
            mediaMsg.put("streamSid", streamSid);
            ObjectNode media = mediaMsg.putObject("media");
            media.put("payload", audioBase64);

            mediaSession.wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(mediaMsg)));
        } catch (Exception e) {
            log.error("Failed to send audio to stream {}: {}", streamSid, e.getMessage());
        }
    }

    /**
     * Send a mark event to track audio playback completion.
     */
    public void sendMark(String streamSid, String markName) {
        MediaSession mediaSession = sessions.get(streamSid);
        if (mediaSession == null || mediaSession.wsSession == null) return;

        try {
            ObjectNode markMsg = objectMapper.createObjectNode();
            markMsg.put("event", "mark");
            markMsg.put("streamSid", streamSid);
            markMsg.putObject("mark").put("name", markName);

            mediaSession.wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(markMsg)));
        } catch (Exception e) {
            log.error("Failed to send mark to stream {}: {}", streamSid, e.getMessage());
        }
    }

    /**
     * Clear any queued audio on the stream.
     */
    public void clearAudio(String streamSid) {
        MediaSession mediaSession = sessions.get(streamSid);
        if (mediaSession == null || mediaSession.wsSession == null) return;

        try {
            ObjectNode clearMsg = objectMapper.createObjectNode();
            clearMsg.put("event", "clear");
            clearMsg.put("streamSid", streamSid);

            mediaSession.wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(clearMsg)));
        } catch (Exception e) {
            log.error("Failed to clear audio on stream {}: {}", streamSid, e.getMessage());
        }
    }

    // --- Internal event handlers ---

    private void handleConnected(WebSocketSession session, JsonNode payload) {
        log.debug("Media stream connected event: {}", payload);
    }

    private void handleStart(WebSocketSession session, JsonNode payload) {
        String streamSid = payload.path("streamSid").asText();
        JsonNode start = payload.path("start");
        String callSid = start.path("callSid").asText();

        // Extract custom parameters
        JsonNode customParams = start.path("customParameters");
        String callId = customParams.path("callId").asText(callSid);
        String authToken = customParams.path("authToken").asText("");

        // Validate auth token
        if (telephonyProvider instanceof TwilioTelephonyProvider twilio) {
            if (!twilio.validateStreamAuthToken(callId, authToken)) {
                log.warn("Invalid stream auth token for callId={}, closing", callId);
                try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) {}
                return;
            }
            twilio.registerActiveStream(callSid, streamSid);
        }

        MediaSession mediaSession = new MediaSession(session, callId, callSid, streamSid);
        sessions.put(streamSid, mediaSession);
        sessionStreamMap.put(session.getId(), streamSid);

        log.info("Media stream started: streamSid={}, callId={}, callSid={}", streamSid, callId, callSid);

        // Notify call manager that the stream is active
        callManager.getCall(callId).ifPresent(call -> {
            NormalizedEvent activeEvent = new NormalizedEvent.CallActive(
                    UUID.randomUUID().toString(),
                    "stream-start:" + streamSid,
                    callId, callSid, Instant.now());
            callManager.processEvent(activeEvent);
        });
    }

    private void handleMedia(WebSocketSession session, JsonNode payload) {
        String streamSid = payload.path("streamSid").asText();
        MediaSession mediaSession = sessions.get(streamSid);
        if (mediaSession == null) return;

        // Audio payload is Base64-encoded mu-law
        String audioBase64 = payload.path("media").path("payload").asText();
        if (audioBase64.isEmpty()) return;

        // Forward to STT processing (would integrate with OpenAI Realtime here)
        mediaSession.audioChunkCount++;
    }

    private void handleStop(WebSocketSession session, JsonNode payload) {
        String streamSid = payload.path("streamSid").asText();
        MediaSession mediaSession = sessions.remove(streamSid);
        if (mediaSession != null) {
            sessionStreamMap.remove(session.getId());
            log.info("Media stream stopped: streamSid={}, callId={}, chunks={}",
                    streamSid, mediaSession.callId, mediaSession.audioChunkCount);
        }
    }

    private void handleMark(WebSocketSession session, JsonNode payload) {
        String streamSid = payload.path("streamSid").asText();
        String markName = payload.path("mark").path("name").asText();
        log.debug("Mark received on stream {}: {}", streamSid, markName);
    }

    // --- Internal session record ---

    private static class MediaSession {
        final WebSocketSession wsSession;
        final String callId;
        final String callSid;
        final String streamSid;
        int audioChunkCount;

        MediaSession(WebSocketSession wsSession, String callId, String callSid, String streamSid) {
            this.wsSession = wsSession;
            this.callId = callId;
            this.callSid = callSid;
            this.streamSid = streamSid;
        }
    }
}
