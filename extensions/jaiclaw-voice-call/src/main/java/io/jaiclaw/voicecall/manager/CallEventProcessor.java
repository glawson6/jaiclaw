package io.jaiclaw.voicecall.manager;

import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.voicecall.model.*;
import io.jaiclaw.voicecall.store.CallStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Processes normalized call events: deduplication, state transitions,
 * transcript management, and call auto-registration.
 */
public class CallEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(CallEventProcessor.class);

    private final Map<String, CallRecord> activeCalls;     // tenant-scoped keys
    private final Map<String, String> providerCallIdMap;    // values are tenant-scoped activeCalls keys
    private final Set<String> processedDedupeKeys;
    private final Set<String> rejectedProviderCallIds;
    private final CallStore callStore;
    private final InboundPolicy inboundPolicy;
    private final Consumer<CallRecord> onCallAnswered;
    private final TenantGuard tenantGuard;

    public CallEventProcessor(Map<String, CallRecord> activeCalls,
                              Map<String, String> providerCallIdMap,
                              CallStore callStore,
                              InboundPolicy inboundPolicy,
                              Consumer<CallRecord> onCallAnswered,
                              TenantGuard tenantGuard) {
        this.activeCalls = activeCalls;
        this.providerCallIdMap = providerCallIdMap;
        this.processedDedupeKeys = ConcurrentHashMap.newKeySet();
        this.rejectedProviderCallIds = ConcurrentHashMap.newKeySet();
        this.callStore = callStore;
        this.inboundPolicy = inboundPolicy;
        this.onCallAnswered = onCallAnswered;
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
    }

    /** Legacy 5-arg constructor — uses a SINGLE-mode TenantGuard. */
    public CallEventProcessor(Map<String, CallRecord> activeCalls,
                              Map<String, String> providerCallIdMap,
                              CallStore callStore,
                              InboundPolicy inboundPolicy,
                              Consumer<CallRecord> onCallAnswered) {
        this(activeCalls, providerCallIdMap, callStore, inboundPolicy, onCallAnswered,
                new TenantGuard(TenantProperties.DEFAULT));
    }

    /** Build the tenant-scoped activeCalls key. */
    private String callKey(String tenantId, String callId) {
        return (tenantId == null ? tenantGuard.getProperties().defaultTenantId() : tenantId) + ":" + callId;
    }

    /** Resolve the current thread's tenantId, falling back to defaultTenantId. */
    private String currentTenantId() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) return ctx.getTenantId();
        return tenantGuard.getProperties().defaultTenantId();
    }

    /**
     * Process a single normalized event. Returns true if the event was handled.
     */
    public boolean processEvent(NormalizedEvent event) {
        // Deduplicate
        String dedupeKey = event.dedupeKey();
        if (dedupeKey != null && !processedDedupeKeys.add(dedupeKey)) {
            log.debug("Duplicate event skipped: {}", dedupeKey);
            return false;
        }

        // Reject events for previously rejected calls
        if (rejectedProviderCallIds.contains(event.providerCallId())) {
            return false;
        }

        // Resolve callId: check provider mapping first, then use event's callId.
        // The mapping value is a tenant-scoped key; split it to recover both fields.
        String resolvedKey = resolveActiveCallsKey(event);
        CallRecord call = resolvedKey == null ? null : activeCalls.get(resolvedKey);

        // Auto-register untracked calls (inbound or externally initiated)
        if (call == null && event instanceof NormalizedEvent.CallInitiated initiated) {
            call = autoRegisterCall(initiated);
            if (call == null) {
                return false; // Rejected by policy
            }
            resolvedKey = callKey(call.getTenantId(), call.getCallId());
        }

        if (call == null) {
            log.debug("Event for unknown call {}, skipping", event.callId());
            return false;
        }

        // Skip already-processed events per call
        if (call.hasProcessedEvent(event.id())) {
            return false;
        }
        call.markEventProcessed(event.id());

        // Update provider call ID mapping (now -> tenant-scoped key)
        if (event.providerCallId() != null && !event.providerCallId().isBlank()) {
            call.setProviderCallId(event.providerCallId());
            providerCallIdMap.put(event.providerCallId(), resolvedKey);
        }

        // Apply event-specific logic
        applyEvent(call, event);

        // Persist
        callStore.persist(call);

        return true;
    }

    private void applyEvent(CallRecord call, NormalizedEvent event) {
        switch (event) {
            case NormalizedEvent.CallInitiated e -> {
                CallLifecycle.transitionState(call, CallState.INITIATED);
            }
            case NormalizedEvent.CallRinging e -> {
                CallLifecycle.transitionState(call, CallState.RINGING);
            }
            case NormalizedEvent.CallAnswered e -> {
                CallLifecycle.transitionState(call, CallState.ANSWERED);
                call.setAnsweredAt(e.timestamp());
                if (onCallAnswered != null) {
                    onCallAnswered.accept(call);
                }
            }
            case NormalizedEvent.CallActive e -> {
                CallLifecycle.transitionState(call, CallState.ACTIVE);
            }
            case NormalizedEvent.CallSpeaking e -> {
                CallLifecycle.transitionState(call, CallState.SPEAKING);
                call.addTranscriptEntry(TranscriptEntry.Speaker.BOT, e.text());
            }
            case NormalizedEvent.CallSpeech e -> {
                if (e.isFinal()) {
                    call.addTranscriptEntry(TranscriptEntry.Speaker.USER, e.transcript());
                }
            }
            case NormalizedEvent.CallSilence e -> {
                log.debug("Silence detected on call {}: {}ms", call.getCallId(), e.durationMs());
            }
            case NormalizedEvent.CallDtmf e -> {
                log.debug("DTMF on call {}: {}", call.getCallId(), e.digits());
            }
            case NormalizedEvent.CallEnded e -> {
                finalizeCall(call, e.reason(), e.timestamp());
            }
            case NormalizedEvent.CallError e -> {
                log.error("Call error on {}: {} (retryable={})", call.getCallId(), e.error(), e.retryable());
                if (!e.retryable()) {
                    finalizeCall(call, EndReason.ERROR, e.timestamp());
                }
            }
        }
    }

    private CallRecord autoRegisterCall(NormalizedEvent.CallInitiated event) {
        // Check inbound policy
        if (event.direction() == CallDirection.INBOUND) {
            if (!inboundPolicy.shouldAcceptInbound(event.from())) {
                rejectedProviderCallIds.add(event.providerCallId());
                log.info("Rejected inbound call from {}", event.from());
                return null;
            }
        }

        // Tenant comes from the currently-set thread context. CallManager.processEvent
        // sets this before delegating, using the providerCallId→tenant mapping when
        // available, falling back to defaultTenantId for brand-new inbound calls.
        String tenantId = currentTenantId();
        CallRecord call = new CallRecord(
                event.callId(), "twilio", event.direction(),
                event.from(), event.to(), CallMode.CONVERSATION);
        call.setTenantId(tenantId);
        call.setProviderCallId(event.providerCallId());

        String key = callKey(tenantId, call.getCallId());
        activeCalls.put(key, call);
        if (event.providerCallId() != null) {
            providerCallIdMap.put(event.providerCallId(), key);
        }

        log.info("Auto-registered {} call: tenant={}, callId={}", event.direction(), tenantId, call.getCallId());
        return call;
    }

    private void finalizeCall(CallRecord call, EndReason reason, Instant endedAt) {
        call.setEndedAt(endedAt);
        call.setEndReason(reason);
        CallState terminalState = mapEndReasonToState(reason);
        CallLifecycle.transitionState(call, terminalState);

        String key = callKey(call.getTenantId(), call.getCallId());
        activeCalls.remove(key);
        if (call.getProviderCallId() != null) {
            providerCallIdMap.remove(call.getProviderCallId());
        }

        log.info("Call {} finalized: tenant={}, state={}, reason={}",
                call.getCallId(), call.getTenantId(), call.getState(), reason);
    }

    /**
     * Resolve the tenant-scoped activeCalls key for the given event. Returns
     * null if no mapping exists (caller treats as "not registered").
     */
    private String resolveActiveCallsKey(NormalizedEvent event) {
        // Check provider mapping first (value is already a tenant-scoped key)
        if (event.providerCallId() != null) {
            String mapped = providerCallIdMap.get(event.providerCallId());
            if (mapped != null) return mapped;
        }
        // Fall back to current-thread tenant + event's callId (the case where
        // CallManager set a context based on providerCallIdMap → tenant lookup,
        // or for outbound calls that don't yet have a provider mapping).
        return event.callId() == null ? null : callKey(currentTenantId(), event.callId());
    }

    private CallState mapEndReasonToState(EndReason reason) {
        return switch (reason) {
            case USER_HANGUP -> CallState.HANGUP_USER;
            case BOT_HANGUP -> CallState.HANGUP_BOT;
            case COMPLETED -> CallState.COMPLETED;
            case TIMEOUT, MAX_DURATION, SILENCE_TIMEOUT -> CallState.TIMEOUT;
            case NO_ANSWER -> CallState.NO_ANSWER;
            case BUSY -> CallState.BUSY;
            case VOICEMAIL -> CallState.VOICEMAIL;
            case ERROR, NETWORK_ERROR -> CallState.ERROR;
            case REJECTED -> CallState.FAILED;
            case UNKNOWN -> CallState.COMPLETED;
        };
    }
}
