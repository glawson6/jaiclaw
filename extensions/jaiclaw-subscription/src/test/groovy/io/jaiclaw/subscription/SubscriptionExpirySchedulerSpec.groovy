package io.jaiclaw.subscription

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class SubscriptionExpirySchedulerSpec extends Specification {

    SubscriptionService subscriptionService = Mock()

    def "scheduler starts and stops without error"() {
        given:
        def scheduler = new SubscriptionExpiryScheduler(subscriptionService, "group1", Duration.ofMinutes(5))

        when:
        scheduler.start()

        then:
        noExceptionThrown()

        when:
        scheduler.stop()

        then:
        noExceptionThrown()
    }

    def "scheduler uses configured group id"() {
        given:
        def scheduler = new SubscriptionExpiryScheduler(subscriptionService, "group1", Duration.ofSeconds(1))

        when:
        scheduler.start()
        // Give it time for one tick
        Thread.sleep(1500)
        scheduler.stop()

        then:
        (1.._) * subscriptionService.processExpired("group1") >> []
    }

    def "scheduled task restores the originating tenant context"() {
        given:
        AtomicReference<TenantContext> observedContext = new AtomicReference<>()
        subscriptionService.processExpired(_) >> { String gid ->
            observedContext.set(TenantContextHolder.get())
            return []
        }
        TenantContextHolder.set(new DefaultTenantContext("tenant-a", "tenant-a"))
        def scheduler = new SubscriptionExpiryScheduler(subscriptionService, "group1", Duration.ofSeconds(1))

        when:
        scheduler.start()
        TenantContextHolder.clear()  // simulate the calling thread moving on
        Thread.sleep(1500)
        scheduler.stop()

        then:
        observedContext.get() != null
        observedContext.get().getTenantId() == "tenant-a"

        cleanup:
        TenantContextHolder.clear()
    }
}
