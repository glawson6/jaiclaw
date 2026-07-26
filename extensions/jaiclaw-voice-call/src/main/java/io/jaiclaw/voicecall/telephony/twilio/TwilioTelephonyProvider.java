package io.jaiclaw.voicecall.telephony.twilio;

import tools.jackson.databind.JsonNode;
import io.jaiclaw.voicecall.config.VoiceCallProperties;
import io.jaiclaw.voicecall.model.*;
import io.jaiclaw.voicecall.telephony.TelephonyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Twilio telephony provider implementation.
 * Handles webhook parsing, call initiation, TwiML generation, and media stream management.
 */
public class TwilioTelephonyProvider implements TelephonyProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioTelephonyProvider.class);

    private final TwilioApiClient apiClient;
    private final TwilioWebhookVerifier verifier;
    private final VoiceCallProperties properties;

    // Tracks TwiML responses to serve on follow-up webhooks
    private final ConcurrentHashMap<String, String> twimlStorage = new ConcurrentHashMap<>();
    // Tracks calls in notify mode (no streaming)
    private final Set<String> notifyCalls = ConcurrentHashMap.newKeySet();
    // Tracks calls with active media streams
    private final Set<String> activeStreamCalls = ConcurrentHashMap.newKeySet();
    // Maps call SID to stream SID
    private final ConcurrentHashMap<String, String> callStreamMap = new ConcurrentHashMap<>();
    // Per-call auth tokens for media stream auth
    private final ConcurrentHashMap<String, String> streamAuthTokens = new ConcurrentHashMap<>();

    public TwilioTelephonyProvider(TwilioApiClient apiClient, VoiceCallProperties properties) {
        this.apiClient = apiClient;
        this.verifier = new TwilioWebhookVerifier(
                properties.twilio() != null ? properties.twilio().authToken() : null);
        this.properties = properties;
    }

    @Override
    public String name() {
        return "twilio";
    }

    @Override
    public WebhookVerificationResult verifyWebhook(WebhookContext ctx) {
        return verifier.verify(ctx);
    }

    @Override
    public WebhookParseResult parseWebhookEvent(WebhookContext ctx) {
        Map<String, String> params = parseFormBody(ctx.rawBody());

        String callSid = params.get("CallSid");
        String callStatus = params.get("CallStatus");
        String direction = params.get("Direction");
        String from = params.get("From");
        String to = params.get("To");
        String speechResult = params.get("SpeechResult");
        String digits = params.get("Digits");

        if (callSid == null || callSid.isBlank()) {
            return WebhookParseResult.withTwiml(List.of(), TwimlGenerator.empty());
        }

        // Build a deduplication key from available fields
        String dedupeKey = buildDedupeKey(ctx, callSid, callStatus);

        String callId = ctx.queryParams().getOrDefault("callId", callSid);
        String turnToken = ctx.queryParams().get("turnToken");

        List<NormalizedEvent> events = new ArrayList<>();
        Instant now = Instant.now();

        // Map Twilio status to normalized events
        if (callStatus != null) {
            switch (callStatus.toLowerCase()) {
                case "initiated" -> events.add(new NormalizedEvent.CallInitiated(
                        UUID.randomUUID().toString(), dedupeKey, callId, callSid, now,
                        from, to, "inbound".equalsIgnoreCase(direction)
                                ? CallDirection.INBOUND : CallDirection.OUTBOUND));

                case "ringing" -> events.add(new NormalizedEvent.CallRinging(
                        UUID.randomUUID().toString(), dedupeKey, callId, callSid, now));

                case "in-progress" -> events.add(new NormalizedEvent.CallAnswered(
                        UUID.randomUUID().toString(), dedupeKey, callId, callSid, now));

                case "completed" -> events.add(new NormalizedEvent.CallEnded(
                        UUID.randomUUID().toString(), dedupeKey, callId, callSid, now,
                        EndReason.COMPLETED));

                case "busy" -> events.add(new NormalizedEvent.CallEnded(
                        UUID.randomUUID().toString(), dedupeKey, callId, callSid, now,
                        EndReason.BUSY));

                case "no-answer" -> events.add(new NormalizedEvent.CallEnded(
                        UUID.randomUUID().toString(), dedupeKey, callId, callSid, now,
                        EndReason.NO_ANSWER));

                case "failed" -> events.add(new NormalizedEvent.CallError(
                        UUID.randomUUID().toString(), dedupeKey, callId, callSid, now,
                        "Call failed", false));

                case "canceled" -> events.add(new NormalizedEvent.CallEnded(
                        UUID.randomUUID().toString(), dedupeKey, callId, callSid, now,
                        EndReason.USER_HANGUP));
            }
        }

        // Handle speech recognition results
        if (speechResult != null && !speechResult.isBlank()) {
            double confidence = 0.0;
            String confStr = params.get("Confidence");
            if (confStr != null) {
                try { confidence = Double.parseDouble(confStr); } catch (NumberFormatException ignored) {}
            }
            events.add(new NormalizedEvent.CallSpeech(
                    UUID.randomUUID().toString(), dedupeKey + ":speech", callId, callSid, now,
                    speechResult, true, confidence));
        }

        // Handle DTMF
        if (digits != null && !digits.isBlank()) {
            events.add(new NormalizedEvent.CallDtmf(
                    UUID.randomUUID().toString(), dedupeKey + ":dtmf", callId, callSid, now,
                    digits));
        }

        // Determine TwiML response
        String twiml = determineTwimlResponse(callSid, callStatus, callId);

        return WebhookParseResult.withTwiml(events, twiml);
    }

    @Override
    public CompletableFuture<InitiateCallResult> initiateCall(InitiateCallInput input) {
        return CompletableFuture.supplyAsync(() -> {
            String webhookUrl = buildWebhookUrl(input.callId());

            JsonNode response = apiClient.createCall(input.from(), input.to(), webhookUrl);
            String callSid = response.path("sid").asText();

            log.info("Twilio call initiated: callId={}, callSid={}", input.callId(), callSid);
            return new InitiateCallResult(callSid, "initiated");
        });
    }

    @Override
    public CompletableFuture<Void> hangupCall(HangupCallInput input) {
        return CompletableFuture.runAsync(() -> {
            apiClient.hangupCall(input.providerCallId());
            cleanup(input.providerCallId());
            log.info("Twilio call hung up: {}", input.providerCallId());
        });
    }

    @Override
    public CompletableFuture<Void> playTts(PlayTtsInput input) {
        return CompletableFuture.runAsync(() -> {
            // If there's an active media stream, TTS is handled via the stream
            if (activeStreamCalls.contains(input.providerCallId())) {
                log.debug("TTS via media stream for call {}", input.providerCallId());
                return;
            }
            // Store TwiML for the next webhook callback
            String twiml = TwimlGenerator.notifySay(input.text(), input.voice());
            twimlStorage.put(input.providerCallId(), twiml);
            log.debug("TTS via TwiML for call {}", input.providerCallId());
        });
    }

    @Override
    public CompletableFuture<Void> startListening(StartListeningInput input) {
        return CompletableFuture.runAsync(() -> {
            if (activeStreamCalls.contains(input.providerCallId())) {
                log.debug("Listening via media stream for call {}", input.providerCallId());
                return;
            }
            String webhookUrl = buildWebhookUrl(input.callId());
            String twiml = TwimlGenerator.gatherSpeech(
                    webhookUrl, input.language(), 30, input.turnToken());
            twimlStorage.put(input.providerCallId(), twiml);
        });
    }

    @Override
    public CompletableFuture<Void> stopListening(StopListeningInput input) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<CallStatusResult> getCallStatus(GetCallStatusInput input) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode response = apiClient.getCallStatus(input.providerCallId());
                String status = response.path("status").asText("unknown");
                boolean terminal = Set.of("completed", "busy", "no-answer", "canceled", "failed")
                        .contains(status);
                return new CallStatusResult(status, terminal, false);
            } catch (Exception e) {
                log.warn("Failed to get call status for {}: {}", input.providerCallId(), e.getMessage());
                return new CallStatusResult("unknown", false, true);
            }
        });
    }

    // --- Internal helpers ---

    /**
     * Store a TwiML response to be returned on the next webhook for this call.
     */
    public void storeTwiml(String callSid, String twiml) {
        twimlStorage.put(callSid, twiml);
    }

    /**
     * Register a call as having an active media stream.
     */
    public void registerActiveStream(String callSid, String streamSid) {
        activeStreamCalls.add(callSid);
        callStreamMap.put(callSid, streamSid);
    }

    /**
     * Generate and store an auth token for a media stream.
     */
    public String generateStreamAuthToken(String callId) {
        String token = UUID.randomUUID().toString();
        streamAuthTokens.put(callId, token);
        return token;
    }

    /**
     * Validate a media stream auth token.
     */
    public boolean validateStreamAuthToken(String callId, String token) {
        String expected = streamAuthTokens.get(callId);
        return expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Mark a call as notify-mode.
     */
    public void markNotifyCall(String callSid) {
        notifyCalls.add(callSid);
    }

    private String determineTwimlResponse(String callSid, String callStatus, String callId) {
        // Check for stored TwiML first
        String stored = twimlStorage.remove(callSid);
        if (stored != null) {
            return stored;
        }

        // For answered calls in conversation mode, start a media stream
        if ("in-progress".equalsIgnoreCase(callStatus) && !notifyCalls.contains(callSid)) {
            String publicUrl = properties.serve() != null ? properties.serve().publicUrl() : null;
            if (publicUrl != null) {
                String streamUrl = publicUrl.replace("https://", "wss://")
                        .replace("http://", "ws://") + "/voice/media-stream";
                String authToken = generateStreamAuthToken(callId);
                return TwimlGenerator.connectStream(streamUrl, callId, authToken);
            }
        }

        // Default: keep the call alive with a long pause
        return TwimlGenerator.pause(120);
    }

    private String buildWebhookUrl(String callId) {
        String publicUrl = properties.serve() != null ? properties.serve().publicUrl() : "";
        String webhookPath = properties.serve() != null ? properties.serve().webhookPath() : "/voice/webhook";
        return publicUrl + webhookPath + "?callId=" + callId;
    }

    private String buildDedupeKey(WebhookContext ctx, String callSid, String callStatus) {
        String signature = ctx.headers().getOrDefault("x-twilio-signature",
                ctx.headers().getOrDefault("X-Twilio-Signature", ""));
        return callSid + ":" + (callStatus != null ? callStatus : "") + ":" + signature.hashCode();
    }

    private Map<String, String> parseFormBody(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private void cleanup(String callSid) {
        twimlStorage.remove(callSid);
        notifyCalls.remove(callSid);
        activeStreamCalls.remove(callSid);
        callStreamMap.remove(callSid);
        // Auth tokens are cleaned by callId, not callSid
    }
}
