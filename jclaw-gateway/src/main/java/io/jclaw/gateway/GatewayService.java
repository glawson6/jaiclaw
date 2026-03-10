package io.jclaw.gateway;

import io.jclaw.agent.AgentRuntime;
import io.jclaw.agent.AgentRuntimeContext;
import io.jclaw.agent.session.SessionManager;
import io.jclaw.channel.*;
import io.jclaw.core.model.AssistantMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core gateway service that bridges channel adapters to the agent runtime.
 * Handles inbound message routing and outbound response delivery.
 */
public class GatewayService implements ChannelMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final AgentRuntime agentRuntime;
    private final SessionManager sessionManager;
    private final ChannelRegistry channelRegistry;
    private final String defaultAgentId;

    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId) {
        this.agentRuntime = agentRuntime;
        this.sessionManager = sessionManager;
        this.channelRegistry = channelRegistry;
        this.defaultAgentId = defaultAgentId;
    }

    /**
     * Handle an inbound message from any channel adapter.
     * Routes to the agent runtime and delivers the response back through the originating channel.
     */
    @Override
    public void onMessage(ChannelMessage message) {
        String sessionKey = message.sessionKey(defaultAgentId);
        log.info("Inbound message on {}: sessionKey={}", message.channelId(), sessionKey);

        var session = sessionManager.getOrCreate(sessionKey, defaultAgentId);
        var context = new AgentRuntimeContext(defaultAgentId, sessionKey, session);

        agentRuntime.run(message.content(), context)
                .thenAccept(response -> deliverResponse(message, response))
                .exceptionally(ex -> {
                    log.error("Failed to process message for session {}", sessionKey, ex);
                    deliverErrorResponse(message, ex.getMessage());
                    return null;
                });
    }

    /**
     * Synchronous message handling — used by the REST API.
     */
    public AssistantMessage handleSync(String channelId, String accountId, String peerId, String content) {
        var inbound = ChannelMessage.inbound(
                UUID.randomUUID().toString(), channelId, accountId, peerId, content, null);
        String sessionKey = inbound.sessionKey(defaultAgentId);

        var session = sessionManager.getOrCreate(sessionKey, defaultAgentId);
        var context = new AgentRuntimeContext(defaultAgentId, sessionKey, session);

        return agentRuntime.run(content, context).join();
    }

    /**
     * Async message handling — returns future for WebSocket streaming.
     */
    public CompletableFuture<AssistantMessage> handleAsync(String sessionKey, String content) {
        var session = sessionManager.getOrCreate(sessionKey, defaultAgentId);
        var context = new AgentRuntimeContext(defaultAgentId, sessionKey, session);
        return agentRuntime.run(content, context);
    }

    private void deliverResponse(ChannelMessage inbound, AssistantMessage response) {
        var outbound = ChannelMessage.outbound(
                UUID.randomUUID().toString(),
                inbound.channelId(),
                inbound.accountId(),
                inbound.peerId(),
                response.content());

        channelRegistry.get(inbound.channelId()).ifPresentOrElse(
                adapter -> {
                    var result = adapter.sendMessage(outbound);
                    if (result instanceof DeliveryResult.Failure f) {
                        log.warn("Failed to deliver response on {}: {} - {}",
                                inbound.channelId(), f.errorCode(), f.message());
                    }
                },
                () -> log.warn("No adapter found for channel: {}", inbound.channelId())
        );
    }

    private void deliverErrorResponse(ChannelMessage inbound, String errorMessage) {
        var outbound = ChannelMessage.outbound(
                UUID.randomUUID().toString(),
                inbound.channelId(),
                inbound.accountId(),
                inbound.peerId(),
                "I encountered an error processing your message. Please try again.");

        channelRegistry.get(inbound.channelId())
                .ifPresent(adapter -> adapter.sendMessage(outbound));
    }

    /**
     * Start all channel adapters.
     */
    public void start() {
        channelRegistry.startAll(this);
        log.info("Gateway started with {} channel adapters", channelRegistry.size());
    }

    /**
     * Stop all channel adapters.
     */
    public void stop() {
        channelRegistry.stopAll();
        log.info("Gateway stopped");
    }
}
