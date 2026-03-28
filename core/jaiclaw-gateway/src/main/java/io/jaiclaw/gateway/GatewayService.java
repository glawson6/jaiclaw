package io.jaiclaw.gateway;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.channel.*;
import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.config.TenantAgentConfigService;
import io.jaiclaw.core.model.AgentIdentity;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolProfileHolder;
import io.jaiclaw.gateway.attachment.AttachmentRouter;
import io.jaiclaw.gateway.channel.TenantChannelAdapterRegistry;
import io.jaiclaw.gateway.tenant.TenantResolver;
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
    private final TenantGuard tenantGuard;
    private final TenantAgentConfigService tenantAgentConfigService;
    private final TenantChannelAdapterRegistry tenantChannelAdapterRegistry;

    public static Builder builder() { return new Builder(); }

    @Deprecated
    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId) {
        this(agentRuntime, sessionManager, channelRegistry, defaultAgentId, null, null, null, null, null);
    }

    @Deprecated
    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId,
                          TenantResolver tenantResolver) {
        this(agentRuntime, sessionManager, channelRegistry, defaultAgentId, tenantResolver, null, null, null, null);
    }

    @Deprecated
    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId,
                          TenantResolver tenantResolver,
                          AttachmentRouter attachmentRouter) {
        this(agentRuntime, sessionManager, channelRegistry, defaultAgentId, tenantResolver, attachmentRouter, null, null, null);
    }

    @Deprecated
    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId,
                          TenantResolver tenantResolver,
                          AttachmentRouter attachmentRouter,
                          TenantGuard tenantGuard) {
        this(agentRuntime, sessionManager, channelRegistry, defaultAgentId, tenantResolver, attachmentRouter, tenantGuard, null, null);
    }

    public GatewayService(AgentRuntime agentRuntime,
                          SessionManager sessionManager,
                          ChannelRegistry channelRegistry,
                          String defaultAgentId,
                          TenantResolver tenantResolver,
                          AttachmentRouter attachmentRouter,
                          TenantGuard tenantGuard,
                          TenantAgentConfigService tenantAgentConfigService,
                          TenantChannelAdapterRegistry tenantChannelAdapterRegistry) {
        this.agentRuntime = agentRuntime;
        this.sessionManager = sessionManager;
        this.channelRegistry = channelRegistry;
        this.defaultAgentId = defaultAgentId;
        this.tenantResolver = tenantResolver;
        this.attachmentRouter = attachmentRouter;
        this.tenantGuard = tenantGuard;
        this.tenantAgentConfigService = tenantAgentConfigService;
        this.tenantChannelAdapterRegistry = tenantChannelAdapterRegistry;
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

        // Fail-closed in MULTI mode: reject unresolved tenant
        if (tenantGuard != null && tenantGuard.isMultiTenant() && tenant.isEmpty()) {
            log.warn("MULTI mode: rejected message on {} — no tenant resolved", message.channelId());
            deliverErrorResponse(message, "No tenant context could be resolved for this request.");
            return;
        }

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

            // Resolve per-tenant agent config if available
            String tenantId = tenant.map(TenantContext::getTenantId).orElse("default");
            TenantAgentConfig tenantConfig = null;
            if (tenantAgentConfigService != null) {
                tenantConfig = tenantAgentConfigService.resolve(tenantId);
            }

            var session = sessionManager.getOrCreate(sessionKey, defaultAgentId);
            ToolProfile toolProfile = ToolProfileHolder.getOrDefault();

            // Build identity from tenant config or use default
            AgentIdentity identity = tenantConfig != null && tenantConfig.identity() != null
                    ? new AgentIdentity(
                        tenantConfig.agentId(),
                        tenantConfig.identity().name(),
                        tenantConfig.identity().description())
                    : AgentIdentity.DEFAULT;

            String agentId = tenantConfig != null ? tenantConfig.agentId() : defaultAgentId;

            AgentRuntimeContext context = new AgentRuntimeContext(
                    agentId, sessionKey, session, identity, toolProfile, ".", tenantConfig);

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
                io.jaiclaw.core.model.AgentIdentity.DEFAULT, toolProfile, ".");

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
                io.jaiclaw.core.model.AgentIdentity.DEFAULT, toolProfile, ".");
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

        // Try tenant-specific adapter first, then fall back to global
        Optional<ChannelAdapter> adapter = Optional.empty();
        if (tenantChannelAdapterRegistry != null) {
            TenantContext tc = TenantContextHolder.get();
            if (tc != null) {
                adapter = tenantChannelAdapterRegistry.getAdapter(tc.getTenantId(), inbound.channelId());
            }
        }
        if (adapter.isEmpty()) {
            adapter = channelRegistry.get(inbound.channelId());
        }

        adapter.ifPresentOrElse(
                a -> {
                    DeliveryResult result = a.sendMessage(outbound);
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

    public static final class Builder {
        private AgentRuntime agentRuntime;
        private SessionManager sessionManager;
        private ChannelRegistry channelRegistry;
        private String defaultAgentId;
        private TenantResolver tenantResolver;
        private AttachmentRouter attachmentRouter;
        private TenantGuard tenantGuard;
        private TenantAgentConfigService tenantAgentConfigService;
        private TenantChannelAdapterRegistry tenantChannelAdapterRegistry;

        public Builder agentRuntime(AgentRuntime agentRuntime) { this.agentRuntime = agentRuntime; return this; }
        public Builder sessionManager(SessionManager sessionManager) { this.sessionManager = sessionManager; return this; }
        public Builder channelRegistry(ChannelRegistry channelRegistry) { this.channelRegistry = channelRegistry; return this; }
        public Builder defaultAgentId(String defaultAgentId) { this.defaultAgentId = defaultAgentId; return this; }
        public Builder tenantResolver(TenantResolver tenantResolver) { this.tenantResolver = tenantResolver; return this; }
        public Builder attachmentRouter(AttachmentRouter attachmentRouter) { this.attachmentRouter = attachmentRouter; return this; }
        public Builder tenantGuard(TenantGuard tenantGuard) { this.tenantGuard = tenantGuard; return this; }
        public Builder tenantAgentConfigService(TenantAgentConfigService tenantAgentConfigService) { this.tenantAgentConfigService = tenantAgentConfigService; return this; }
        public Builder tenantChannelAdapterRegistry(TenantChannelAdapterRegistry tenantChannelAdapterRegistry) { this.tenantChannelAdapterRegistry = tenantChannelAdapterRegistry; return this; }

        public GatewayService build() {
            return new GatewayService(agentRuntime, sessionManager, channelRegistry, defaultAgentId,
                    tenantResolver, attachmentRouter, tenantGuard, tenantAgentConfigService,
                    tenantChannelAdapterRegistry);
        }
    }
}
