package io.jaiclaw.subscription.provider

import io.jaiclaw.subscription.PaymentEventType
import io.jaiclaw.subscription.SubscriptionPlan
import spock.lang.Specification

import java.math.BigDecimal
import java.time.Duration

class PayPalPaymentProviderSpec extends Specification {

    def plan = new SubscriptionPlan(
            "monthly", "Monthly Plan", "Test plan",
            Duration.ofDays(30), new BigDecimal("9.99"), "USD", Map.of())

    def "handleWebhook parses PAYMENT.CAPTURE.COMPLETED event"() {
        given:
        def provider = new PayPalPaymentProvider(
                "client-id", "client-secret", true,
                "webhook-id", "https://example.com/return", "https://example.com/cancel")

        def payload = '''
        {
            "id": "WH-123",
            "event_type": "PAYMENT.CAPTURE.COMPLETED",
            "resource": {
                "custom_id": "sub-456"
            }
        }
        '''

        when:
        def result = provider.handleWebhook(payload, Map.of())

        then:
        result.isPresent()
        result.get().id() == "WH-123"
        result.get().subscriptionId() == "sub-456"
        result.get().type() == PaymentEventType.CHECKOUT_COMPLETED
    }

    def "handleWebhook returns empty for unknown event type"() {
        given:
        def provider = new PayPalPaymentProvider(
                "client-id", "client-secret", true,
                "webhook-id", "https://example.com/return", "https://example.com/cancel")

        def payload = '{"id":"WH-999","event_type":"CUSTOMER.DISPUTE.CREATED","resource":{}}'

        when:
        def result = provider.handleWebhook(payload, Map.of())

        then:
        result.isEmpty()
    }

    def "handleWebhook returns empty when verifyWebhook is enabled but verification fails"() {
        given:
        // verifyWebhook=true but no actual PayPal API available — will fail verification
        def provider = new PayPalPaymentProvider(
                "client-id", "client-secret", true,
                "webhook-id", "https://example.com/return", "https://example.com/cancel",
                true)

        def payload = '{"id":"WH-123","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"custom_id":"sub-1"}}'

        when:
        def result = provider.handleWebhook(payload, Map.of())

        then:
        // Verification will fail (no real API) → returns empty
        result.isEmpty()
    }

    def "handleWebhook skips verification when verifyWebhook is disabled"() {
        given:
        def provider = new PayPalPaymentProvider(
                "client-id", "client-secret", true,
                "webhook-id", "https://example.com/return", "https://example.com/cancel",
                false)

        def payload = '{"id":"WH-123","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"custom_id":"sub-1"}}'

        when:
        def result = provider.handleWebhook(payload, Map.of())

        then:
        result.isPresent()
        result.get().type() == PaymentEventType.CHECKOUT_COMPLETED
    }

    def "handleWebhook parses BILLING.SUBSCRIPTION.CANCELLED event"() {
        given:
        def provider = new PayPalPaymentProvider(
                "client-id", "client-secret", true,
                "webhook-id", "https://example.com/return", "https://example.com/cancel")

        def payload = '{"id":"WH-456","event_type":"BILLING.SUBSCRIPTION.CANCELLED","resource":{"custom_id":"sub-789"}}'

        when:
        def result = provider.handleWebhook(payload, Map.of())

        then:
        result.isPresent()
        result.get().type() == PaymentEventType.SUBSCRIPTION_CANCELLED
    }

    def "name returns paypal"() {
        given:
        def provider = new PayPalPaymentProvider(
                "client-id", "client-secret", true,
                "webhook-id", "https://example.com/return", "https://example.com/cancel")

        expect:
        provider.name() == "paypal"
    }
}
