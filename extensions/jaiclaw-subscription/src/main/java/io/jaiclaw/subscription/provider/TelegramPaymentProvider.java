package io.jaiclaw.subscription.provider;

import io.jaiclaw.subscription.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Telegram Payments API provider. Uses the Bot Payments API to create invoices
 * and process payment callbacks. Telegram acts as a frontend; actual payment
 * is handled by the configured payment provider token (e.g. Stripe Connect).
 *
 * <p>Unlike web-based providers, Telegram payments happen in-chat.
 * The "checkout URL" returned is a special protocol string that the
 * Telegram integration layer uses to send an invoice message.</p>
 */
public class TelegramPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(TelegramPaymentProvider.class);

    private final String paymentProviderToken;

    public TelegramPaymentProvider(String paymentProviderToken) {
        this.paymentProviderToken = paymentProviderToken;
    }

    public String getPaymentProviderToken() {
        return paymentProviderToken;
    }

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public CheckoutResult createCheckout(String userId, SubscriptionPlan plan,
                                          Map<String, String> metadata) {
        // Telegram payments are in-chat — we return a special URL that the
        // TelegramAccessController interprets to send an invoice via Bot API
        String invoiceId = UUID.randomUUID().toString().substring(0, 8);

        // Price in smallest currency unit (cents for USD)
        int amount = plan.price().multiply(BigDecimal.valueOf(100)).intValue();

        return new CheckoutResult(
                "telegram://invoice/" + invoiceId,
                invoiceId,
                "telegram",
                Map.of(
                        "provider_token", paymentProviderToken,
                        "title", plan.name(),
                        "description", plan.description() != null ? plan.description() : plan.name(),
                        "currency", plan.currency(),
                        "amount", String.valueOf(amount),
                        "chat_id", userId,
                        "subscription_id", metadata.getOrDefault("subscription_id", "")
                )
        );
    }

    @Override
    public PaymentVerification verifyPayment(String paymentId) {
        // Telegram payment verification happens via pre_checkout_query
        // This is a no-op for post-verification
        return PaymentVerification.success(paymentId, "telegram_verified");
    }

    @Override
    public void cancelSubscription(String externalSubscriptionId) {
        // Telegram doesn't manage recurring subscriptions — cancellation is
        // handled by the SubscriptionService directly
        log.info("Telegram subscription cancellation is handled locally: {}", externalSubscriptionId);
    }

    @Override
    public Optional<PaymentEvent> handleWebhook(String payload, Map<String, String> headers) {
        // Telegram payment events come through the bot update handler, not webhooks.
        // The TelegramAccessController in jaiclaw-subscription-telegram processes these.
        log.debug("Telegram payment webhook not applicable — events handled via bot updates");
        return Optional.empty();
    }
}
