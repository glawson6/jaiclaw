package io.jaiclaw.subscription.telegram

import io.jaiclaw.subscription.AccessChange
import io.jaiclaw.subscription.AccessChangeType
import spock.lang.Specification

import java.time.Instant

class TelegramAccessControllerSpec extends Specification {

    TelegramGroupManager groupManager = Mock()
    TelegramAccessController controller = new TelegramAccessController(
            groupManager, "-100channel", "-100group")

    def "onActivated creates invite links and sends them to user"() {
        given:
        def change = new AccessChange("user1", "-100group", AccessChangeType.GRANT,
                Instant.now(), "Subscription activated")
        groupManager.createInviteLink("-100channel") >> "https://t.me/+channelInvite"
        groupManager.createInviteLink("-100group") >> "https://t.me/+groupInvite"

        when:
        controller.onActivated(change)

        then:
        1 * groupManager.sendMessage("user1", { String msg ->
            msg.contains("active") &&
            msg.contains("https://t.me/+channelInvite") &&
            msg.contains("https://t.me/+groupInvite")
        })
    }

    def "onRevoked removes user from channel and group"() {
        given:
        def change = new AccessChange("user1", "-100group", AccessChangeType.REVOKE,
                Instant.now(), "Subscription expired")

        when:
        controller.onRevoked(change)

        then:
        1 * groupManager.removeUser("-100channel", "user1")
        1 * groupManager.removeUser("-100group", "user1")
        1 * groupManager.sendMessage("user1", { it.contains("ended") })
    }

    def "onPastDue sends warning to user without removing"() {
        given:
        def change = new AccessChange("user1", "-100group", AccessChangeType.REVOKE,
                Instant.now(), "Payment failed")

        when:
        controller.onPastDue(change)

        then:
        1 * groupManager.sendMessage("user1", { it.contains("Warning") && it.contains("payment") })
        0 * groupManager.removeUser(_, _)
    }

    def "onCancelled removes user and sends cancellation message"() {
        given:
        def change = new AccessChange("user1", "-100group", AccessChangeType.REVOKE,
                Instant.now(), "User cancelled")

        when:
        controller.onCancelled(change)

        then:
        1 * groupManager.removeUser("-100channel", "user1")
        1 * groupManager.removeUser("-100group", "user1")
        1 * groupManager.sendMessage("user1", { it.contains("cancelled") })
    }
}
