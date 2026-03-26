package io.jaiclaw.subscription;

import java.util.Map;
import java.util.Optional;

/**
 * SPI for payment providers. Implementations handle checkout creation,
 * payment verification, cancellation, and webhook processing.
 */
public interface PaymentProvider {

    /** Provider name (e.g. "stripe", "telegram", "paypal"). */
    String name();

    /** Create a checkout session for the given user and plan. */
    CheckoutResult createCheckout(String userId, SubscriptionPlan plan, Map<String, String> metadata);

    /** Verify a payment by its provider-specific ID. */
    PaymentVerification verifyPayment(String paymentId);

    /** Cancel an active subscription by its external subscription ID. */
    void cancelSubscription(String externalSubscriptionId);

    /** Process an incoming webhook and return a PaymentEvent if recognized. */
    Optional<PaymentEvent> handleWebhook(String payload, Map<String, String> headers);
}
