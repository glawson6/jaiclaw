package io.jaiclaw.subscription.telegram;

import io.jaiclaw.subscription.AccessChange;
import io.jaiclaw.subscription.AccessChangeType;
import io.jaiclaw.subscription.SubscriptionLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Telegram-specific access controller. Manages user access to private channels
 * and groups based on subscription lifecycle events.
 *
 * <p>On activation: creates one-time invite links for both channel and group,
 * and sends them to the user via DM.
 *
 * <p>On revocation: removes the user from both channel and group
 * (ban then unban to avoid permanent block).
 *
 * <p>On past due: sends a warning DM to the user.
 */
public class TelegramAccessController implements SubscriptionLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(TelegramAccessController.class);

    private final TelegramGroupManager groupManager;
    private final String channelId;
    private final String groupId;

    public TelegramAccessController(TelegramGroupManager groupManager,
                                     String channelId, String groupId) {
        this.groupManager = groupManager;
        this.channelId = channelId;
        this.groupId = groupId;
    }

    @Override
    public void onActivated(AccessChange change) {
        log.info("Granting access to user {}: {}", change.userId(), change.reason());

        // Create one-time invite links
        String channelInvite = groupManager.createInviteLink(channelId);
        String groupInvite = groupManager.createInviteLink(groupId);

        // Send invite links to the user via DM
        var sb = new StringBuilder();
        sb.append("Your subscription is now active! Welcome!\n\n");
        if (channelInvite != null) {
            sb.append("Channel: ").append(channelInvite).append("\n");
        }
        if (groupInvite != null) {
            sb.append("Group: ").append(groupInvite).append("\n");
        }
        sb.append("\nThese invite links can only be used once.");

        groupManager.sendMessage(change.userId(), sb.toString());
    }

    @Override
    public void onRevoked(AccessChange change) {
        log.info("Revoking access for user {}: {}", change.userId(), change.reason());

        // Remove from channel and group (ban then unban)
        groupManager.removeUser(channelId, change.userId());
        groupManager.removeUser(groupId, change.userId());

        groupManager.sendMessage(change.userId(),
                "Your subscription has ended and access has been revoked.\n" +
                        "Reason: " + change.reason());
    }

    @Override
    public void onPastDue(AccessChange change) {
        log.info("Subscription past due for user {}: {}", change.userId(), change.reason());

        groupManager.sendMessage(change.userId(),
                "Warning: Your subscription payment has failed.\n" +
                        "Please update your payment method to avoid losing access.\n" +
                        "Use /subscribe to renew.");
    }

    @Override
    public void onCancelled(AccessChange change) {
        log.info("Subscription cancelled for user {}: {}", change.userId(), change.reason());

        groupManager.removeUser(channelId, change.userId());
        groupManager.removeUser(groupId, change.userId());

        groupManager.sendMessage(change.userId(),
                "Your subscription has been cancelled and access has been revoked.\n" +
                        "Use /subscribe to resubscribe at any time.");
    }
}
