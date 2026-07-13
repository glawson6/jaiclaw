package io.jaiclaw.voicecall.mcp;

import tools.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.voicecall.manager.CallManager;
import io.jaiclaw.voicecall.model.CallMode;
import io.jaiclaw.voicecall.model.CallRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool provider exposing voice call actions: initiate, speak, continue, end, status.
 */
public class VoiceCallMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(VoiceCallMcpToolProvider.class);
    private static final String SERVER_NAME = "voice-call";
    private static final String SERVER_DESCRIPTION = "Voice call tools — initiate phone calls, speak, listen, manage active calls via telephony providers";

    private final CallManager callManager;
    private final ObjectMapper objectMapper;

    public VoiceCallMcpToolProvider(CallManager callManager, ObjectMapper objectMapper) {
        this.callManager = callManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("voice_call_initiate",
                        "Initiate an outbound phone call. Returns the callId for subsequent operations.",
                        INITIATE_SCHEMA),
                new McpToolDefinition("voice_call_continue",
                        "Speak a message to the user on an active call and wait for their response. Returns the user's transcribed speech.",
                        CONTINUE_SCHEMA),
                new McpToolDefinition("voice_call_speak",
                        "Speak a message to the user on an active call without waiting for a response.",
                        SPEAK_SCHEMA),
                new McpToolDefinition("voice_call_end",
                        "End an active phone call.",
                        END_SCHEMA),
                new McpToolDefinition("voice_call_status",
                        "Get the current status of a call including state and transcript.",
                        STATUS_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        try {
            return switch (toolName) {
                case "voice_call_initiate" -> handleInitiate(args);
                case "voice_call_continue" -> handleContinue(args);
                case "voice_call_speak" -> handleSpeak(args);
                case "voice_call_end" -> handleEnd(args);
                case "voice_call_status" -> handleStatus(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (IllegalArgumentException e) {
            return McpToolResult.error("Missing required parameter: " + e.getMessage());
        } catch (Exception e) {
            log.error("Voice call tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    // --- Tool handlers ---

    private McpToolResult handleInitiate(Map<String, Object> args) throws Exception {
        String to = requireString(args, "to");
        String message = stringOrDefault(args, "message", null);
        String modeStr = stringOrDefault(args, "mode", "conversation");
        CallMode mode = "notify".equalsIgnoreCase(modeStr) ? CallMode.NOTIFY : CallMode.CONVERSATION;

        CallRecord call = callManager.initiateCall(to, message, mode)
                .get(30, TimeUnit.SECONDS);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "callId", call.getCallId(),
                "status", call.getState().name().toLowerCase())));
    }

    private McpToolResult handleContinue(Map<String, Object> args) throws Exception {
        String callId = requireString(args, "callId");
        String message = requireString(args, "message");

        String transcript = callManager.speak(callId, message)
                .get(60, TimeUnit.SECONDS);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "callId", callId,
                "transcript", transcript != null ? transcript : "")));
    }

    private McpToolResult handleSpeak(Map<String, Object> args) throws Exception {
        String callId = requireString(args, "callId");
        String message = requireString(args, "message");

        callManager.speakNoWait(callId, message)
                .get(30, TimeUnit.SECONDS);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "callId", callId)));
    }

    private McpToolResult handleEnd(Map<String, Object> args) throws Exception {
        String callId = requireString(args, "callId");

        callManager.endCall(callId)
                .get(15, TimeUnit.SECONDS);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "callId", callId)));
    }

    private McpToolResult handleStatus(Map<String, Object> args) throws Exception {
        String callId = requireString(args, "callId");

        Optional<CallRecord> callOpt = callManager.getCall(callId);
        if (callOpt.isEmpty()) {
            // Check history
            List<CallRecord> history = callManager.getHistory(100);
            callOpt = history.stream()
                    .filter(c -> c.getCallId().equals(callId))
                    .findFirst();
        }

        if (callOpt.isEmpty()) {
            return McpToolResult.error("No call found with id: " + callId);
        }

        CallRecord call = callOpt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("callId", call.getCallId());
        result.put("state", call.getState().name().toLowerCase());
        result.put("direction", call.getDirection().name().toLowerCase());
        result.put("from", call.getFrom());
        result.put("to", call.getTo());
        result.put("isTerminal", call.getState().isTerminal());

        if (call.getEndReason() != null) {
            result.put("endReason", call.getEndReason().name().toLowerCase());
        }

        // Include transcript
        List<Map<String, String>> transcriptEntries = call.getTranscript().stream()
                .map(t -> Map.of(
                        "speaker", t.speaker().name().toLowerCase(),
                        "text", t.text(),
                        "timestamp", t.timestamp().toString()))
                .toList();
        result.put("transcript", transcriptEntries);
        result.put("transcriptCount", transcriptEntries.size());

        return McpToolResult.success(toJson(result));
    }

    // --- Helpers ---

    private String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key);
        }
        return value.toString();
    }

    private String stringOrDefault(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        return (value != null && !value.toString().isBlank()) ? value.toString() : defaultValue;
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    // --- JSON Schema constants ---

    private static final String INITIATE_SCHEMA = """
            {"type":"object","properties":{\
            "to":{"type":"string","description":"Phone number to call (E.164 format, e.g. +15551234567)"},\
            "message":{"type":"string","description":"Optional message to speak when the call connects"},\
            "mode":{"type":"string","description":"Call mode: 'conversation' (interactive, default) or 'notify' (one-way message)"}},\
            "required":["to"]}""";

    private static final String CONTINUE_SCHEMA = """
            {"type":"object","properties":{\
            "callId":{"type":"string","description":"ID of the active call"},\
            "message":{"type":"string","description":"Message to speak to the user before listening for their response"}},\
            "required":["callId","message"]}""";

    private static final String SPEAK_SCHEMA = """
            {"type":"object","properties":{\
            "callId":{"type":"string","description":"ID of the active call"},\
            "message":{"type":"string","description":"Message to speak to the user (does not wait for response)"}},\
            "required":["callId","message"]}""";

    private static final String END_SCHEMA = """
            {"type":"object","properties":{\
            "callId":{"type":"string","description":"ID of the call to end"}},\
            "required":["callId"]}""";

    private static final String STATUS_SCHEMA = """
            {"type":"object","properties":{\
            "callId":{"type":"string","description":"ID of the call to check"}},\
            "required":["callId"]}""";
}
