package io.jaiclaw.subscription.telegram;

import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.subscription.*;
import io.jaiclaw.subscription.provider.TelegramPaymentProvider;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JaiClaw plugin providing subscription bot commands for Telegram:
 * <ul>
 *   <li>/subscribe — show plans and initiate checkout</li>
 *   <li>/status — current subscription status and expiry</li>
 *   <li>/cancel — cancel with confirmation</li>
 * </ul>
 */
public class TelegramSubscriptionPlugin implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(TelegramSubscriptionPlugin.class);

    private final SubscriptionService subscriptionService;
    private final TelegramGroupManager groupManager;
    private final String defaultProvider;
    private final String groupId;

    public TelegramSubscriptionPlugin(SubscriptionService subscriptionService,
                                       TelegramGroupManager groupManager,
                                       String defaultProvider,
                                       String groupId) {
        this.subscriptionService = subscriptionService;
        this.groupManager = groupManager;
        this.defaultProvider = defaultProvider;
        this.groupId = groupId;
    }

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "telegram-subscription-plugin",
                "Telegram Subscription Plugin",
                "Subscription management commands for Telegram",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.on(HookName.MESSAGE_RECEIVED, (event, ctx) -> {
            if (event instanceof ChannelMessage message && "telegram".equals(message.channelId())) {
                String response = handleCommand(message.content(), message.peerId());
                if (response != null) {
                    groupManager.sendMessage(message.peerId(), response);
                }
            }
            return null;
        });
    }

    /**
     * Handle a subscription command. Returns a response string if handled, null otherwise.
     */
    public String handleCommand(String text, String userId) {
        if (text == null || !text.startsWith("/")) return null;

        String command = text.split("\\s+")[0].toLowerCase();
        String args = text.length() > command.length() ? text.substring(command.length()).trim() : "";

        return switch (command) {
            case "/subscribe" -> handleSubscribe(userId, args);
            case "/status" -> handleStatus(userId);
            case "/cancel" -> handleCancel(userId, args);
            default -> null;
        };
    }

    private String handleSubscribe(String userId, String args) {
        // Check if user already has an active subscription
        var existing = subscriptionService.getActiveSubscription(userId);
        if (existing.isPresent()) {
            var sub = existing.get();
            return "You already have an active subscription (plan: %s, expires: %s).\nUse /status for details.".formatted(
                    sub.planId(), sub.expiresAt());
        }

        List<SubscriptionPlan> plans = subscriptionService.listPlans();
        if (plans.isEmpty()) {
            return "No subscription plans are currently available.";
        }

        // If a plan ID is provided, initiate checkout directly
        if (!args.isBlank()) {
            var plan = subscriptionService.getPlan(args);
            if (plan.isEmpty()) {
                return "Unknown plan: " + args + "\nUse /subscribe to see available plans.";
            }

            try {
                var result = subscriptionService.createCheckout(
                        userId, args, defaultProvider, Map.of("user_id", userId));

                // If Telegram provider, send invoice in-chat
                if ("telegram".equals(defaultProvider) && result.metadata().containsKey("provider_token")) {
                    String providerToken = result.metadata().get("provider_token");
                    int amount = Integer.parseInt(result.metadata().getOrDefault("amount", "0"));
                    String currency = result.metadata().getOrDefault("currency", "USD");

                    groupManager.sendInvoice(
                            userId,
                            plan.get().name(),
                            plan.get().description() != null ? plan.get().description() : plan.get().name(),
                            result.sessionId(),
                            providerToken,
                            currency,
                            amount
                    );
                    return null; // Invoice sent as a separate message
                }

                // For web-based providers (Stripe, PayPal), return the checkout URL
                return "Complete your payment here:\n" + result.checkoutUrl();
            } catch (Exception e) {
                log.error("Failed to create checkout: {}", e.getMessage());
                return "Failed to create checkout. Please try again later.";
            }
        }

        // Show available plans
        var sb = new StringBuilder("Available subscription plans:\n\n");
        for (var plan : plans) {
            sb.append("*%s* — %s %s / %s\n".formatted(
                    plan.name(), plan.price(), plan.currency(), formatDuration(plan)));
            if (plan.description() != null) {
                sb.append("  %s\n".formatted(plan.description()));
            }
            sb.append("  → /subscribe %s\n\n".formatted(plan.id()));
        }
        return sb.toString();
    }

    private String handleStatus(String userId) {
        var subscriptions = subscriptionService.getUserSubscriptions(userId);
        if (subscriptions.isEmpty()) {
            return "You don't have any subscriptions.\nUse /subscribe to get started.";
        }

        var sb = new StringBuilder("Your subscriptions:\n\n");
        for (var sub : subscriptions) {
            sb.append("Plan: *%s*\n".formatted(sub.planId()));
            sb.append("Status: %s\n".formatted(sub.status()));
            if (sub.startedAt() != null) sb.append("Started: %s\n".formatted(sub.startedAt()));
            if (sub.expiresAt() != null) sb.append("Expires: %s\n".formatted(sub.expiresAt()));
            sb.append("Provider: %s\n\n".formatted(sub.paymentProvider()));
        }
        return sb.toString();
    }

    private String handleCancel(String userId, String args) {
        var active = subscriptionService.getActiveSubscription(userId);
        if (active.isEmpty()) {
            return "You don't have an active subscription to cancel.";
        }

        var sub = active.get();

        // Require confirmation
        if (!"confirm".equalsIgnoreCase(args)) {
            return "Are you sure you want to cancel your %s subscription?\nThis will revoke your access.\n\nType /cancel confirm to proceed.".formatted(
                    sub.planId());
        }

        subscriptionService.cancel(sub.id(), groupId);
        return "Your subscription has been cancelled.";
    }

    private String formatDuration(SubscriptionPlan plan) {
        long days = plan.duration().toDays();
        if (days <= 31) return "month";
        if (days <= 366) return "year";
        return days + " days";
    }
}
