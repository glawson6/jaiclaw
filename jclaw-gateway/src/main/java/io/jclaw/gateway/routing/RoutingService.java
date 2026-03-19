package io.jclaw.gateway.routing;

import io.jclaw.core.model.ChatType;
import io.jclaw.core.model.RoutingBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Routes inbound messages based on channel, chat type, and @mention rules.
 * In group chats with mentionOnly=true, messages are only processed if the
 * bot is mentioned.
 */
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final List<RoutingBinding> bindings;
    private final String defaultGroupBehavior;

    public RoutingService(List<RoutingBinding> bindings, String defaultGroupBehavior) {
        this.bindings = new ArrayList<>(bindings);
        this.defaultGroupBehavior = defaultGroupBehavior != null ? defaultGroupBehavior : "mention-only";
    }

    /**
     * Determine if a message should be processed by the agent.
     *
     * @param channelId        the channel (telegram, slack, etc.)
     * @param peerId           the peer/chat ID
     * @param chatType         DIRECT, GROUP, CHANNEL, THREAD
     * @param mentionedBotIds  set of bot IDs mentioned in the message
     * @param selfBotId        this bot's ID on the platform
     * @return true if the message should be routed to the agent
     */
    public boolean shouldProcess(String channelId, String peerId, ChatType chatType,
                                  Set<String> mentionedBotIds, String selfBotId) {
        if (chatType == ChatType.DIRECT) return true;

        Optional<RoutingBinding> binding = findBinding(channelId, peerId, chatType);

        boolean mentionOnly;
        if (binding.isPresent()) {
            mentionOnly = binding.get().mentionOnly();
        } else {
            mentionOnly = "mention-only".equals(defaultGroupBehavior);
            if ("ignore".equals(defaultGroupBehavior)) {
                log.debug("Ignoring group message from {}:{} (default=ignore)", channelId, peerId);
                return false;
            }
        }

        if (mentionOnly) {
            boolean mentioned = selfBotId != null && mentionedBotIds.contains(selfBotId);
            if (!mentioned) {
                log.debug("Dropping group message from {}:{} (not mentioned)", channelId, peerId);
                return false;
            }
        }

        return true;
    }

    /**
     * Resolve the agent ID for a given channel/peer/chat combination.
     */
    public String resolveAgentId(String channelId, String peerId, ChatType chatType) {
        return findBinding(channelId, peerId, chatType)
                .map(RoutingBinding::agentId)
                .orElse("default");
    }

    private Optional<RoutingBinding> findBinding(String channelId, String peerId, ChatType chatType) {
        return bindings.stream()
                .filter(b -> b.matches(channelId, peerId, chatType))
                .findFirst();
    }
}
