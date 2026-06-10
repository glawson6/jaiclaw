package io.jaiclaw.voicecall.manager;

import io.jaiclaw.core.tenant.DefaultTenantContext;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantContextPropagator;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.voicecall.config.VoiceCallProperties;
import io.jaiclaw.voicecall.model.*;
import io.jaiclaw.voicecall.store.CallStore;
import io.jaiclaw.voicecall.telephony.TelephonyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Thread-safe call lifecycle manager. Coordinates telephony providers, event processing,
 * and transcript collection.
 */
public class CallManager {

    private static final Logger log = LoggerFactory.getLogger(CallManager.class);

    private final TelephonyProvider telephonyProvider;
    private final CallStore callStore;
    private final VoiceCallProperties properties;
    private final CallEventProcessor eventProcessor;
    private final TenantGuard tenantGuard;

    // Active call state (thread-safe). Keys are tenant-scoped: "{tenantId}:{callId}".
    private final ConcurrentHashMap<String, CallRecord> activeCalls = new ConcurrentHashMap<>();
    // Provider-call-id → tenant-scoped activeCalls key (so we can look up the CallRecord
    // for an inbound webhook event without leaking across tenants).
    private final ConcurrentHashMap<String, String> providerCallIdMap = new ConcurrentHashMap<>();

    // Per-call transcript waiters for conversation mode. Keyed by tenant-scoped callId.
    private final ConcurrentHashMap<String, TranscriptWaiter> transcriptWaiters = new ConcurrentHashMap<>();

    // Max-duration timers per call. Keyed by tenant-scoped callId.
    private final ConcurrentHashMap<String, ScheduledFuture<?>> maxDurationTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    // Callback for answered calls
    private Consumer<CallRecord> onCallAnswered;

    public CallManager(TelephonyProvider telephonyProvider,
                       CallStore callStore,
                       VoiceCallProperties properties,
                       TenantGuard tenantGuard) {
        this.telephonyProvider = telephonyProvider;
        this.callStore = callStore;
        this.properties = properties;
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "call-manager-scheduler");
            t.setDaemon(true);
            return t;
        });

        InboundPolicy inboundPolicy = new InboundPolicy(
                properties.inbound() != null ? properties.inbound() : null);

        this.eventProcessor = new CallEventProcessor(
                activeCalls, providerCallIdMap, callStore, inboundPolicy,
                this::handleCallAnswered, this.tenantGuard);

        // Load active calls from store for recovery. With no tenant context set
        // (typical boot-time), the store returns every tenant's records.
        // Each persisted CallRecord carries its own tenantId, which we use to
        // build the tenant-scoped key.
        Map<String, CallRecord> recovered = callStore.loadActiveCalls();
        for (CallRecord call : recovered.values()) {
            String key = callKey(callTenantId(call), call.getCallId());
            activeCalls.put(key, call);
            if (call.getProviderCallId() != null) {
                providerCallIdMap.put(call.getProviderCallId(), key);
            }
        }
        if (!recovered.isEmpty()) {
            log.info("Recovered {} active calls from store", recovered.size());
        }
    }

    /** Legacy 3-arg constructor — uses a SINGLE-mode TenantGuard. */
    public CallManager(TelephonyProvider telephonyProvider,
                       CallStore callStore,
                       VoiceCallProperties properties) {
        this(telephonyProvider, callStore, properties, new TenantGuard(TenantProperties.DEFAULT));
    }

    /** Build the tenant-scoped activeCalls/timer key. */
    private String callKey(String tenantId, String callId) {
        return (tenantId == null ? tenantGuard.getProperties().defaultTenantId() : tenantId) + ":" + callId;
    }

    /** Resolve a CallRecord's tenantId, falling back to the configured default. */
    private String callTenantId(CallRecord call) {
        if (call != null && call.getTenantId() != null) return call.getTenantId();
        return tenantGuard.getProperties().defaultTenantId();
    }

    /** Resolve the current thread's tenantId, falling back to the configured default. */
    private String currentTenantId() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) return ctx.getTenantId();
        return tenantGuard.getProperties().defaultTenantId();
    }

    /** Look up an active call by raw callId under the current tenant's prefix. */
    private CallRecord findActiveCall(String callId) {
        return activeCalls.get(callKey(currentTenantId(), callId));
    }

    /**
     * Set the callback for when a call is answered.
     */
    public void setOnCallAnswered(Consumer<CallRecord> callback) {
        this.onCallAnswered = callback;
    }

    /**
     * Initiate an outbound call.
     */
    public CompletableFuture<CallRecord> initiateCall(String to, String message, CallMode mode) {
        String callId = UUID.randomUUID().toString();
        String from = properties.outbound() != null ? properties.outbound().fromNumber() : null;
        if (from == null || from.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No fromNumber configured for outbound calls"));
        }

        String tenantId = currentTenantId();
        CallRecord call = new CallRecord(callId, telephonyProvider.name(),
                CallDirection.OUTBOUND, from, to, mode);
        call.setTenantId(tenantId);
        String key = callKey(tenantId, callId);
        activeCalls.put(key, call);
        callStore.persist(call);

        return telephonyProvider.initiateCall(new TelephonyProvider.InitiateCallInput(
                callId, from, to, buildWebhookUrl(callId), null
        )).thenApply(result -> {
            call.setProviderCallId(result.providerCallId());
            providerCallIdMap.put(result.providerCallId(), key);
            CallLifecycle.transitionState(call, CallState.RINGING);
            callStore.persist(call);
            startMaxDurationTimer(call);
            log.info("Call initiated: callId={}, tenant={}, to={}, mode={}", callId, tenantId, to, mode);
            return call;
        }).exceptionally(ex -> {
            log.error("Failed to initiate call to {}: {}", to, ex.getMessage());
            call.setState(CallState.FAILED);
            call.setEndedAt(Instant.now());
            call.setEndReason(EndReason.ERROR);
            activeCalls.remove(key);
            callStore.persist(call);
            throw new CompletionException(ex);
        });
    }

    /**
     * Speak to the user and wait for their response (conversation mode).
     */
    public CompletableFuture<String> speak(String callId, String message) {
        CallRecord call = findActiveCall(callId);
        if (call == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No active call with id: " + callId));
        }

        call.addTranscriptEntry(TranscriptEntry.Speaker.BOT, message);
        CallLifecycle.transitionState(call, CallState.SPEAKING);

        return telephonyProvider.playTts(new TelephonyProvider.PlayTtsInput(
                callId, call.getProviderCallId(), message, null, null
        )).thenCompose(v -> {
            // After speaking, start listening for user response
            return waitForTranscript(callId);
        });
    }

    /**
     * Speak to the user without waiting for a response.
     */
    public CompletableFuture<Void> speakNoWait(String callId, String message) {
        CallRecord call = findActiveCall(callId);
        if (call == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No active call with id: " + callId));
        }

        call.addTranscriptEntry(TranscriptEntry.Speaker.BOT, message);
        CallLifecycle.transitionState(call, CallState.SPEAKING);
        callStore.persist(call);

        return telephonyProvider.playTts(new TelephonyProvider.PlayTtsInput(
                callId, call.getProviderCallId(), message, null, null));
    }

    /**
     * End an active call.
     */
    public CompletableFuture<Void> endCall(String callId) {
        CallRecord call = findActiveCall(callId);
        if (call == null) {
            return CompletableFuture.completedFuture(null);
        }

        return telephonyProvider.hangupCall(new TelephonyProvider.HangupCallInput(
                callId, call.getProviderCallId(), "bot_hangup"
        )).thenRun(() -> {
            finalizeCall(call, EndReason.BOT_HANGUP);
        });
    }

    /**
     * Process an incoming webhook event.
     *
     * <p>Inbound webhooks arrive on a provider-supplied thread with no tenant
     * context. We resolve the owning tenant from the existing
     * {@code providerCallIdMap} (its values are tenant-scoped activeCalls keys),
     * set the {@link TenantContextHolder} for the duration of the call, then
     * clear it in a finally block. New inbound calls (no prior mapping) fall
     * back to the configured default tenant id — typically a SINGLE-mode
     * deployment, where this is correct.
     */
    public void processEvent(NormalizedEvent event) {
        String tenantId = resolveTenantForEvent(event);
        TenantContext priorCtx = TenantContextHolder.get();
        TenantContextHolder.set(new DefaultTenantContext(tenantId, tenantId));
        try {
            eventProcessor.processEvent(event);

            // Check if this event resolves a transcript waiter
            if (event instanceof NormalizedEvent.CallSpeech speech && speech.isFinal()) {
                String callId = resolveCallId(event);
                String key = callKey(tenantId, callId);
                TranscriptWaiter waiter = transcriptWaiters.remove(key);
                if (waiter != null) {
                    waiter.resolve(speech.transcript());
                }
            }
        } finally {
            if (priorCtx != null) {
                TenantContextHolder.set(priorCtx);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    /** Resolve the tenant that owns this inbound event, defaulting to the SINGLE-mode tenantId. */
    private String resolveTenantForEvent(NormalizedEvent event) {
        if (event.providerCallId() != null) {
            String key = providerCallIdMap.get(event.providerCallId());
            if (key != null) {
                int colon = key.indexOf(':');
                if (colon > 0) return key.substring(0, colon);
            }
        }
        return tenantGuard.getProperties().defaultTenantId();
    }

    /**
     * Get an active call by ID for the current tenant.
     */
    public Optional<CallRecord> getCall(String callId) {
        return Optional.ofNullable(findActiveCall(callId));
    }

    /**
     * Get all active calls for the current tenant.
     */
    public Collection<CallRecord> getActiveCalls() {
        String prefix = currentTenantId() + ":";
        return activeCalls.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Get call history.
     */
    public List<CallRecord> getHistory(int limit) {
        return callStore.getHistory(limit);
    }

    /**
     * Clean up stale calls that exceed max duration.
     */
    public void reapStaleCalls() {
        Instant cutoff = Instant.now().minusSeconds(
                properties.outbound() != null ? properties.outbound().maxDurationSec() * 2L : 1200);

        List<CallRecord> staleCalls = activeCalls.values().stream()
                .filter(call -> call.getStartedAt().isBefore(cutoff))
                .toList();

        for (CallRecord call : staleCalls) {
            log.warn("Reaping stale call: tenant={}, callId={}", callTenantId(call), call.getCallId());
            finalizeCall(call, EndReason.TIMEOUT);
        }
    }

    /**
     * Shutdown the scheduler.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    // --- Internal ---

    private CompletableFuture<String> waitForTranscript(String callId) {
        CallRecord call = findActiveCall(callId);
        if (call == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Call not active: " + callId));
        }
        String key = callKey(callTenantId(call), callId);

        String turnToken = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();

        // Start listening
        telephonyProvider.startListening(new TelephonyProvider.StartListeningInput(
                callId, call.getProviderCallId(), null, turnToken));
        CallLifecycle.transitionState(call, CallState.LISTENING);

        // Set up timeout — wrap so the timer thread restores this call's tenant context.
        int timeoutSec = properties.outbound() != null ? properties.outbound().silenceTimeoutSec() : 30;
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(
                TenantContextPropagator.wrap(() -> {
                    TranscriptWaiter waiter = transcriptWaiters.remove(key);
                    if (waiter != null) {
                        waiter.timeout();
                    }
                }),
                timeoutSec, TimeUnit.SECONDS);

        transcriptWaiters.put(key, new TranscriptWaiter(future, timeoutFuture, turnToken));

        return future;
    }

    private void startMaxDurationTimer(CallRecord call) {
        int maxSec = properties.outbound() != null ? properties.outbound().maxDurationSec() : 600;
        String tenantId = callTenantId(call);
        String key = callKey(tenantId, call.getCallId());
        // Wrap so the timer thread restores the originating tenant before calling endCall.
        ScheduledFuture<?> timer = scheduler.schedule(
                TenantContextPropagator.wrap(() -> {
                    log.warn("Call {} (tenant={}) reached max duration ({} seconds)",
                            call.getCallId(), tenantId, maxSec);
                    endCall(call.getCallId());
                }),
                maxSec, TimeUnit.SECONDS);
        maxDurationTimers.put(key, timer);
    }

    private void finalizeCall(CallRecord call, EndReason reason) {
        if (call == null) return;
        String tenantId = callTenantId(call);
        String key = callKey(tenantId, call.getCallId());
        if (activeCalls.remove(key) == null) return;

        call.setEndedAt(Instant.now());
        call.setEndReason(reason);

        CallState terminalState = switch (reason) {
            case USER_HANGUP -> CallState.HANGUP_USER;
            case BOT_HANGUP -> CallState.HANGUP_BOT;
            case TIMEOUT, MAX_DURATION, SILENCE_TIMEOUT -> CallState.TIMEOUT;
            default -> CallState.COMPLETED;
        };
        call.setState(terminalState);

        if (call.getProviderCallId() != null) {
            providerCallIdMap.remove(call.getProviderCallId());
        }

        // Cancel timers
        ScheduledFuture<?> timer = maxDurationTimers.remove(key);
        if (timer != null) timer.cancel(false);

        // Reject any pending transcript waiter
        TranscriptWaiter waiter = transcriptWaiters.remove(key);
        if (waiter != null) waiter.cancel();

        callStore.persist(call);
        log.info("Call finalized: callId={}, tenant={}, state={}, reason={}",
                call.getCallId(), tenantId, terminalState, reason);
    }

    private void handleCallAnswered(CallRecord call) {
        if (onCallAnswered != null) {
            onCallAnswered.accept(call);
        }
    }

    private String resolveCallId(NormalizedEvent event) {
        if (event.providerCallId() != null) {
            String mapped = providerCallIdMap.get(event.providerCallId());
            if (mapped != null) {
                // providerCallIdMap values are "tenant:callId" — strip the prefix
                // so the caller sees the raw call id.
                int colon = mapped.indexOf(':');
                return colon > 0 ? mapped.substring(colon + 1) : mapped;
            }
        }
        return event.callId();
    }

    private String buildWebhookUrl(String callId) {
        String publicUrl = properties.serve() != null ? properties.serve().publicUrl() : "";
        String path = properties.serve() != null ? properties.serve().webhookPath() : "/voice/webhook";
        return publicUrl + path + "?callId=" + callId;
    }

    // --- Transcript waiter helper ---

    private static class TranscriptWaiter {
        private final CompletableFuture<String> future;
        private final ScheduledFuture<?> timeoutFuture;
        private final String turnToken;

        TranscriptWaiter(CompletableFuture<String> future, ScheduledFuture<?> timeoutFuture,
                         String turnToken) {
            this.future = future;
            this.timeoutFuture = timeoutFuture;
            this.turnToken = turnToken;
        }

        void resolve(String transcript) {
            timeoutFuture.cancel(false);
            future.complete(transcript);
        }

        void timeout() {
            future.complete(""); // Empty transcript on timeout
        }

        void cancel() {
            timeoutFuture.cancel(false);
            future.cancel(false);
        }
    }
}
