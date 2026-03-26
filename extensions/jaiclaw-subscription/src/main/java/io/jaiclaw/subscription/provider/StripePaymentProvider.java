package io.jaiclaw.subscription.provider;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.jaiclaw.subscription.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Stripe payment provider using Checkout Sessions for payment collection
 * and webhook signature verification for event processing.
 */
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final String webhookSecret;
    private final String successUrl;
    private final String cancelUrl;

    public StripePaymentProvider(String apiKey, String webhookSecret,
                                 String successUrl, String cancelUrl) {
        Stripe.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public CheckoutResult createCheckout(String userId, SubscriptionPlan plan,
                                          Map<String, String> metadata) {
        try {
            // Convert price to cents
            long unitAmount = plan.price().multiply(BigDecimal.valueOf(100)).longValue();

            var params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(plan.currency().toLowerCase())
                                    .setUnitAmount(unitAmount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(plan.name())
                                            .setDescription(plan.description())
                                            .build())
                                    .setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                            .setInterval(mapDurationToInterval(plan))
                                            .build())
                                    .build())
                            .setQuantity(1L)
                            .build())
                    .putAllMetadata(metadata)
                    .build();

            Session session = Session.create(params);

            return new CheckoutResult(session.getUrl(), session.getId(), "stripe",
                    Map.of("stripe_session_id", session.getId()));
        } catch (StripeException e) {
            log.error("Failed to create Stripe checkout: {}", e.getMessage());
            throw new RuntimeException("Stripe checkout creation failed", e);
        }
    }

    @Override
    public PaymentVerification verifyPayment(String paymentId) {
        try {
            Session session = Session.retrieve(paymentId);
            String status = session.getPaymentStatus();
            boolean paid = "paid".equals(status);
            return paid
                    ? PaymentVerification.success(paymentId, status)
                    : PaymentVerification.failure("Payment status: " + status);
        } catch (StripeException e) {
            return PaymentVerification.failure("Verification failed: " + e.getMessage());
        }
    }

    @Override
    public void cancelSubscription(String externalSubscriptionId) {
        try {
            com.stripe.model.Subscription sub =
                    com.stripe.model.Subscription.retrieve(externalSubscriptionId);
            sub.cancel();
            log.info("Cancelled Stripe subscription: {}", externalSubscriptionId);
        } catch (StripeException e) {
            log.error("Failed to cancel Stripe subscription {}: {}", externalSubscriptionId, e.getMessage());
            throw new RuntimeException("Stripe cancellation failed", e);
        }
    }

    @Override
    public Optional<PaymentEvent> handleWebhook(String payload, Map<String, String> headers) {
        String sigHeader = headers.get("stripe-signature");
        if (sigHeader == null) {
            log.warn("Missing Stripe signature header");
            return Optional.empty();
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return Optional.empty();
        }

        String type = event.getType();
        PaymentEventType eventType = switch (type) {
            case "checkout.session.completed" -> PaymentEventType.CHECKOUT_COMPLETED;
            case "invoice.payment_succeeded" -> PaymentEventType.PAYMENT_SUCCEEDED;
            case "invoice.payment_failed" -> PaymentEventType.PAYMENT_FAILED;
            case "customer.subscription.deleted" -> PaymentEventType.SUBSCRIPTION_CANCELLED;
            default -> null;
        };

        if (eventType == null) {
            log.debug("Ignoring Stripe event type: {}", type);
            return Optional.empty();
        }

        // Extract subscription ID from event metadata
        String subscriptionId = null;
        if (event.getData() != null && event.getData().getObject() != null) {
            var obj = event.getData().getObject();
            if (obj instanceof Session session) {
                subscriptionId = session.getMetadata() != null
                        ? session.getMetadata().get("subscription_id") : null;
            }
        }

        return Optional.of(new PaymentEvent(
                event.getId(),
                subscriptionId,
                "stripe",
                eventType,
                null,
                null,
                Instant.ofEpochSecond(event.getCreated()),
                Map.of("stripe_event_type", type, "stripe_event_id", event.getId())
        ));
    }

    private SessionCreateParams.LineItem.PriceData.Recurring.Interval mapDurationToInterval(
            SubscriptionPlan plan) {
        long days = plan.duration().toDays();
        if (days <= 31) return SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH;
        return SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR;
    }
}
