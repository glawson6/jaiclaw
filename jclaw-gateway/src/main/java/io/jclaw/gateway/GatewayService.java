package io.jclaw.gateway;

import io.jclaw.agent.AgentRuntime;
import io.jclaw.agent.AgentRuntimeContext;
import io.jclaw.agent.session.SessionManager;
import io.jclaw.channel.*;
import io.jclaw.core.model.AssistantMessage;
import io.jclaw.core.tenant.TenantContext;
import io.jclaw.core.tenant.TenantContextHolder;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolProfileHolder;
import io.jclaw.gateway.attachment.AttachmentRouter;
import io.jclaw.gateway.tenant.TenantResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core gateway service that bridges channel adapters to the agent runtime.
 * Handles inbound message routing and outbound response delivery.
 * Sets {@link TenantContextHolder} before agent execution if a {@link TenantResolver} is configured.
 */
public class GatewayService implements ChannelMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final AgentRuntime agentRuntime;
    private final SessionManager sessionManager;
    private final ChannelRegistry channelRegistry;
    private final String defaultAgentId;
    private final TenantResolver tenantResolver;
    private final AttachmentRouter attachmentRouter;

    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId) {
        this(agentRuntime, sessionManager, channelRegistry, defaultAgentId, null, null);
    }

    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId,
                          TenantResolver tenantResolver) {
        this(agentRuntime, sessionManager, channelRegistry, defaultAgentId, tenantResolver, null);
    }

    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId,
                          TenantResolver tenantResolver,
                          AttachmentRouter attachmentRouter) {
        this.agentRuntime = agentRuntime;
        this.sessionManager = sessionManager;
        this.channelRegistry = channelRegistry;
        this.defaultAgentId = defaultAgentId;
        this.tenantResolver = tenantResolver;
        this.attachmentRouter = attachmentRouter;
    }

    /**
     * Handle an inbound message from any channel adapter.
     * Routes to the agent runtime and delivers the response back through the originating channel.
     */
    @Override
    public void onMessage(ChannelMessage message) {
        String sessionKey = message.sessionKey(defaultAgentId);
        log.info("Inbound message on {}: sessionKey={}", message.channelId(), sessionKey);

        // Resolve tenant from channel metadata
        Optional<TenantContext> tenant = resolveTenantFromChannel(message);
        tenant.ifPresent(TenantContextHolder::set);

        try {
            // Route attachments if present
            if (message.hasAttachments() && attachmentRouter != null) {
                TenantContext tc = tenant.orElse(null);
                for (var attachment : message.attachments()) {
                    try {
                        var payload = AttachmentPayload.of(
                                attachment.name(), attachment.mimeType(), attachment.data());
                        attachmentRouter.route(payload, message, tc);
                    } catch (Exception e) {
                        log.warn("Failed to route attachment {}: {}", attachment.name(), e.getMessage());
                    }
                }
            }

            var session = sessionManager.getOrCreate(sessionKey, defaultAgentId);
            ToolProfile toolProfile = ToolProfileHolder.getOrDefault();
            AgentRuntimeContext context = new AgentRuntimeContext(
                    defaultAgentId, sessionKey, session,
                    io.jclaw.core.model.AgentIdentity.DEFAULT, toolProfile, ".");

            agentRuntime.run(message.content(), context)
                    .thenAccept(response -> deliverResponse(message, response))
                    .exceptionally(ex -> {
                        log.error("Failed to process message for session {}", sessionKey, ex);
                        deliverErrorResponse(message, ex.getMessage());
                        return null;
                    });
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Synchronous message handling — used by the REST API.
     * Caller must set TenantContextHolder before calling this method.
     */
    public AssistantMessage handleSync(String channelId, String accountId, String peerId, String content) {
        ChannelMessage inbound = ChannelMessage.inbound(
                UUID.randomUUID().toString(), channelId, accountId, peerId, content, null);
        String sessionKey = inbound.sessionKey(defaultAgentId);

        var session = sessionManager.getOrCreate(sessionKey, defaultAgentId);
        ToolProfile toolProfile = ToolProfileHolder.getOrDefault();
        AgentRuntimeContext context = new AgentRuntimeContext(
                defaultAgentId, sessionKey, session,
                io.jclaw.core.model.AgentIdentity.DEFAULT, toolProfile, ".");

        return agentRuntime.run(content, context).join();
    }

    /**
     * Async message handling — returns future for WebSocket streaming.
     * Caller must set TenantContextHolder before calling this method.
     */
    public CompletableFuture<AssistantMessage> handleAsync(String sessionKey, String content) {
        var session = sessionManager.getOrCreate(sessionKey, defaultAgentId);
        ToolProfile toolProfile = ToolProfileHolder.getOrDefault();
        AgentRuntimeContext context = new AgentRuntimeContext(
                defaultAgentId, sessionKey, session,
                io.jclaw.core.model.AgentIdentity.DEFAULT, toolProfile, ".");
        return agentRuntime.run(content, context);
    }

    /**
     * Resolve tenant from request attributes (used by GatewayController for REST requests).
     */
    public Optional<TenantContext> resolveTenant(Map<String, String> attributes) {
        if (tenantResolver == null) return Optional.empty();
        return tenantResolver.resolve(attributes);
    }

    private Optional<TenantContext> resolveTenantFromChannel(ChannelMessage message) {
        if (tenantResolver == null) return Optional.empty();
        return tenantResolver.resolve(Map.of(
                "channelId", message.channelId(),
                "accountId", message.accountId() != null ? message.accountId() : ""
        ));
    }

    private void deliverResponse(ChannelMessage inbound, AssistantMessage response) {
        ChannelMessage outbound = ChannelMessage.outbound(
                UUID.randomUUID().toString(),
                inbound.channelId(),
                inbound.accountId(),
                inbound.peerId(),
                response.content());

        channelRegistry.get(inbound.channelId()).ifPresentOrElse(
                adapter -> {
                    DeliveryResult result = adapter.sendMessage(outbound);
                    if (result instanceof DeliveryResult.Failure f) {
                        log.warn("Failed to deliver response on {}: {} - {}",
                                inbound.channelId(), f.errorCode(), f.message());
                    }
                },
                () -> log.warn("No adapter found for channel: {}", inbound.channelId())
        );
    }

    private void deliverErrorResponse(ChannelMessage inbound, String errorMessage) {
        ChannelMessage outbound = ChannelMessage.outbound(
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
