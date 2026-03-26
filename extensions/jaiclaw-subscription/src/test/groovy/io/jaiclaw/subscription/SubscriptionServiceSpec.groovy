package io.jaiclaw.subscription

import spock.lang.Specification

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class SubscriptionServiceSpec extends Specification {

    SubscriptionRepository repository = Mock()
    PaymentProvider stripeProvider = Mock()
    SubscriptionLifecycleListener listener = Mock()

    SubscriptionService service

    def setup() {
        stripeProvider.name() >> "stripe"
        def plans = [
                new SubscriptionPlan("monthly", "Monthly", "Monthly access",
                        Duration.ofDays(30), BigDecimal.valueOf(9.99), "USD", Map.of()),
                new SubscriptionPlan("yearly", "Yearly", "Yearly access",
                        Duration.ofDays(365), BigDecimal.valueOf(99.99), "USD", Map.of())
        ]
        service = new SubscriptionService(repository, [stripeProvider], [listener], plans)
    }

    def "listPlans returns all configured plans"() {
        expect:
        service.listPlans().size() == 2
        service.listPlans().collect { it.id() } as Set == ["monthly", "yearly"] as Set
    }

    def "getPlan returns plan by id"() {
        expect:
        service.getPlan("monthly").isPresent()
        service.getPlan("monthly").get().price() == BigDecimal.valueOf(9.99)
        service.getPlan("nonexistent").isEmpty()
    }

    def "createCheckout creates subscription and delegates to provider"() {
        given:
        def checkoutResult = new CheckoutResult("https://stripe.com/checkout", "sess_123", "stripe", Map.of())
        stripeProvider.createCheckout(_, _, _) >> checkoutResult

        when:
        def result = service.createCheckout("user1", "monthly", "stripe", Map.of())

        then:
        result.checkoutUrl() == "https://stripe.com/checkout"
        result.sessionId() == "sess_123"
        1 * repository.save({ it.userId() == "user1" && it.planId() == "monthly" })
    }

    def "createCheckout throws for unknown plan"() {
        when:
        service.createCheckout("user1", "nonexistent", "stripe", Map.of())

        then:
        thrown(IllegalArgumentException)
    }

    def "createCheckout throws for unknown provider"() {
        when:
        service.createCheckout("user1", "monthly", "unknown", Map.of())

        then:
        thrown(IllegalArgumentException)
    }

    def "activate transitions subscription and fires listener"() {
        given:
        def sub = new Subscription("sub1", "user1", "monthly", SubscriptionStatus.ACTIVE,
                null, null, "stripe", "ext1", Map.of())
        repository.findById("sub1") >> Optional.of(sub)

        when:
        def activated = service.activate("sub1", "group1")

        then:
        activated.status() == SubscriptionStatus.ACTIVE
        activated.startedAt() != null
        activated.expiresAt() != null
        1 * repository.save({ it.status() == SubscriptionStatus.ACTIVE && it.startedAt() != null })
        1 * listener.onActivated({ it.userId() == "user1" && it.type() == AccessChangeType.GRANT })
    }

    def "cancel transitions subscription and fires listener"() {
        given:
        def sub = new Subscription("sub1", "user1", "monthly", SubscriptionStatus.ACTIVE,
                Instant.now(), Instant.now().plus(Duration.ofDays(30)), "stripe", "ext1", Map.of())
        repository.findById("sub1") >> Optional.of(sub)

        when:
        def cancelled = service.cancel("sub1", "group1")

        then:
        cancelled.status() == SubscriptionStatus.CANCELLED
        1 * stripeProvider.cancelSubscription("ext1")
        1 * repository.save({ it.status() == SubscriptionStatus.CANCELLED })
        1 * listener.onCancelled({ it.userId() == "user1" && it.type() == AccessChangeType.REVOKE })
    }

    def "processExpired finds and transitions expired subscriptions"() {
        given:
        def expired = new Subscription("sub1", "user1", "monthly", SubscriptionStatus.ACTIVE,
                Instant.now().minus(Duration.ofDays(31)), Instant.now().minus(Duration.ofDays(1)),
                "stripe", "ext1", Map.of())
        repository.findExpired(_) >> [expired]

        when:
        def processed = service.processExpired("group1")

        then:
        processed.size() == 1
        1 * repository.save({ it.status() == SubscriptionStatus.EXPIRED })
        1 * listener.onRevoked({ it.userId() == "user1" })
    }

    def "markPastDue transitions subscription and fires listener"() {
        given:
        def sub = new Subscription("sub1", "user1", "monthly", SubscriptionStatus.ACTIVE,
                Instant.now(), Instant.now().plus(Duration.ofDays(30)), "stripe", "ext1", Map.of())
        repository.findById("sub1") >> Optional.of(sub)

        when:
        def updated = service.markPastDue("sub1", "group1")

        then:
        updated.status() == SubscriptionStatus.PAST_DUE
        1 * repository.save({ it.status() == SubscriptionStatus.PAST_DUE })
        1 * listener.onPastDue({ it.userId() == "user1" })
    }

    def "getActiveSubscription returns active subscription for user"() {
        given:
        def active = new Subscription("sub1", "user1", "monthly", SubscriptionStatus.ACTIVE,
                Instant.now(), Instant.now().plus(Duration.ofDays(30)), "stripe", "ext1", Map.of())
        def expired = new Subscription("sub2", "user1", "monthly", SubscriptionStatus.EXPIRED,
                Instant.now().minus(Duration.ofDays(60)), Instant.now().minus(Duration.ofDays(30)),
                "stripe", "ext2", Map.of())
        repository.findByUserId("user1") >> [active, expired]

        when:
        def result = service.getActiveSubscription("user1")

        then:
        result.isPresent()
        result.get().id() == "sub1"
    }

    def "handleWebhook delegates to correct provider"() {
        given:
        def event = new PaymentEvent("evt1", "sub1", "stripe",
                PaymentEventType.CHECKOUT_COMPLETED, null, null, Instant.now(), Map.of())
        stripeProvider.handleWebhook("payload", [:]) >> Optional.of(event)

        when:
        def result = service.handleWebhook("stripe", "payload", [:])

        then:
        result.isPresent()
        result.get().type() == PaymentEventType.CHECKOUT_COMPLETED
    }

    def "handleWebhook returns empty for unknown provider"() {
        expect:
        service.handleWebhook("unknown", "payload", [:]).isEmpty()
    }
}
