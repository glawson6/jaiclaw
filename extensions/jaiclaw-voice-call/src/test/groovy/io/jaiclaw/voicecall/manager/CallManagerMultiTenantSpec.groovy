package io.jaiclaw.voicecall.manager

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.voicecall.config.VoiceCallProperties
import io.jaiclaw.voicecall.model.*
import io.jaiclaw.voicecall.store.InMemoryCallStore
import io.jaiclaw.voicecall.telephony.TelephonyProvider
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.CompletableFuture

class CallManagerMultiTenantSpec extends Specification {

    TenantGuard multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "ignored", false))
    TelephonyProvider provider = Mock()
    InMemoryCallStore store = new InMemoryCallStore(multiGuard)

    VoiceCallProperties props = new VoiceCallProperties(
            true, "twilio", null, null, null,
            new VoiceCallProperties.OutboundProperties(
                    "+15550000000", null, CallMode.CONVERSATION, 600, 30),
            null)
    CallManager manager

    def setup() {
        provider.name() >> "twilio"
        provider.initiateCall(_) >> { TelephonyProvider.InitiateCallInput input ->
            CompletableFuture.completedFuture(
                    new TelephonyProvider.InitiateCallResult("provider-" + input.callId(), null))
        }
        manager = new CallManager(provider, store, props, multiGuard)
    }

    def cleanup() { TenantContextHolder.clear() }

    private void setTenant(String id) {
        TenantContextHolder.set(new DefaultTenantContext(id, id))
    }

    def "two tenants initiating calls have isolated active-call views"() {
        when:
        setTenant("tenant-a")
        CallRecord aCall = manager.initiateCall("+15551111", "hi", CallMode.NOTIFY).join()
        setTenant("tenant-b")
        CallRecord bCall = manager.initiateCall("+15552222", "hi", CallMode.NOTIFY).join()

        then:
        aCall.tenantId == "tenant-a"
        bCall.tenantId == "tenant-b"

        when:
        setTenant("tenant-a")
        Collection<CallRecord> aActive = manager.getActiveCalls()
        setTenant("tenant-b")
        Collection<CallRecord> bActive = manager.getActiveCalls()

        then:
        aActive.size() == 1
        aActive[0].callId == aCall.callId
        bActive.size() == 1
        bActive[0].callId == bCall.callId
    }

    def "getCall is tenant-filtered: tenant B cannot read tenant A's call"() {
        given:
        setTenant("tenant-a")
        CallRecord aCall = manager.initiateCall("+15551111", "hi", CallMode.NOTIFY).join()

        when:
        setTenant("tenant-b")
        Optional<CallRecord> seen = manager.getCall(aCall.callId)

        then:
        seen.isEmpty()
    }

    def "processEvent restores the originating tenant from the providerCallId mapping"() {
        given:
        setTenant("tenant-a")
        CallRecord aCall = manager.initiateCall("+15551111", "hi", CallMode.NOTIFY).join()

        // Simulate webhook thread with no context
        TenantContextHolder.clear()

        when:
        manager.processEvent(new NormalizedEvent.CallActive(
                "evt-1", "dedup-1", aCall.callId, aCall.providerCallId, Instant.now()))

        then:
        // After processEvent returns, the original (cleared) context is restored.
        TenantContextHolder.get() == null

        when:
        // The call's state advanced to ACTIVE — which only happens if the event
        // processor found the tenant-scoped record under the right key.
        setTenant("tenant-a")
        Optional<CallRecord> seen = manager.getCall(aCall.callId)

        then:
        seen.isPresent()
        seen.get().state == CallState.ACTIVE
    }
}
