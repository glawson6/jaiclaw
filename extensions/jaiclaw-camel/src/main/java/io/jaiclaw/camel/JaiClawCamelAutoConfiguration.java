package io.jaiclaw.camel;

import io.jaiclaw.channel.ChannelMessageHandler;
import io.jaiclaw.channel.ChannelRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Auto-configuration for Camel channel adapters with SEDA-based routing.
 *
 * <p>Supports three discovery modes for channel definitions:
 * <ol>
 *   <li><strong>YAML</strong> — {@code jaiclaw.camel.channels} configuration properties</li>
 *   <li><strong>Annotation</strong> — {@link JaiClawChannel} on {@link RouteBuilder} beans</li>
 *   <li><strong>Route properties</strong> — {@code jaiclaw.channelId} property on Camel routes</li>
 * </ol>
 *
 * <p>After registration, creates bridge routes for each adapter:
 * inbound source bridges, outbound URI/channel bridges, or logger fallbacks.
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration",
        "org.apache.camel.spring.boot.CamelAutoConfiguration"
})
@ConditionalOnClass(name = "org.apache.camel.CamelContext")
@EnableConfigurationProperties(CamelChannelProperties.class)
public class JaiClawCamelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawCamelAutoConfiguration.class);

    @Bean
    @ConditionalOnBean({CamelContext.class, ChannelRegistry.class})
    public ApplicationRunner registerCamelChannels(
            CamelChannelProperties properties,
            CamelContext camelContext,
            ChannelRegistry channelRegistry,
            ObjectProvider<ChannelMessageHandler> messageHandlerProvider,
            ObjectProvider<List<RouteBuilder>> routeBuildersProvider) {
        return args -> {
            Map<String, CamelChannelAdapter> adapters = new LinkedHashMap<>();

            // Phase 1: Register from YAML
            for (CamelChannelConfig config : properties.channels()) {
                registerAdapter(config, camelContext, channelRegistry, adapters);
            }

            // Phase 2: Discover from @JaiClawChannel annotations
            List<RouteBuilder> routeBuilders = routeBuildersProvider.getIfAvailable();
            if (routeBuilders != null) {
                for (RouteBuilder routeBuilder : routeBuilders) {
                    JaiClawChannel annotation = routeBuilder.getClass().getAnnotation(JaiClawChannel.class);
                    if (annotation != null) {
                        CamelChannelConfig config = new CamelChannelConfig(
                                annotation.channelId(),
                                emptyToNull(annotation.displayName()),
                                emptyToNull(annotation.accountId()),
                                emptyToNull(annotation.outboundUri()),
                                null, // no inboundUri from annotation — user defines from() in RouteBuilder
                                emptyToNull(annotation.outbound()),
                                annotation.stateless()
                        );
                        registerAdapter(config, camelContext, channelRegistry, adapters);
                    }
                }
            }

            // Phase 3: Discover from route properties
            for (Route route : camelContext.getRoutes()) {
                Map<String, Object> props = route.getProperties();
                if (props == null) {
                    continue;
                }
                String channelId = Objects.toString(props.get("jaiclaw.channelId"), null);
                if (channelId == null || channelId.isBlank()) {
                    continue;
                }
                if (adapters.containsKey(channelId) || channelRegistry.contains(channelId)) {
                    log.debug("Skipping route-property channel '{}' — already registered", channelId);
                    continue;
                }
                CamelChannelConfig config = new CamelChannelConfig(
                        channelId,
                        Objects.toString(props.get("jaiclaw.displayName"), null),
                        Objects.toString(props.get("jaiclaw.accountId"), null),
                        Objects.toString(props.get("jaiclaw.outboundUri"), null),
                        null, // no inboundUri from route properties
                        Objects.toString(props.get("jaiclaw.outbound"), null),
                        "true".equalsIgnoreCase(Objects.toString(props.get("jaiclaw.stateless"), "false"))
                );
                registerAdapter(config, camelContext, channelRegistry, adapters);
            }

            // Phase 4: Create bridge routes and track wiring for diagram
            Map<String, String[]> wiring = new LinkedHashMap<>();

            for (CamelChannelAdapter adapter : adapters.values()) {
                CamelChannelConfig config = adapter.config();
                String inLabel;
                String outLabel;

                // Inbound bridge
                if (config.inboundUri() != null && !config.inboundUri().isBlank()) {
                    adapter.createInboundBridge(config.inboundUri());
                    inLabel = config.inboundUri();
                } else {
                    inLabel = findRouteTargeting(camelContext, adapter.inboundEndpoint());
                }

                // Outbound bridge
                if (config.outboundUri() != null && !config.outboundUri().isBlank()) {
                    adapter.createOutboundUriBridge(config.outboundUri());
                    outLabel = config.outboundUri();
                } else if (config.outbound() != null && !config.outbound().isBlank()) {
                    adapter.createOutboundChannelBridge(channelRegistry, config.outbound());
                    outLabel = "channel:" + config.outbound();
                } else if (!hasConsumerForEndpoint(camelContext, adapter.outboundEndpoint())) {
                    adapter.createLoggerFallback();
                    outLabel = "(logger)";
                } else {
                    log.info("Channel '{}' outbound SEDA already has a consumer — skipping bridge",
                            config.channelId());
                    outLabel = findRouteConsumingFrom(camelContext, adapter.outboundEndpoint());
                }

                wiring.put(config.channelId(), new String[]{inLabel, outLabel});
            }

            // Phase 5: Start adapters (GatewayLifecycle.start() runs before ApplicationRunner,
            // so late-registered adapters miss startAll — start them explicitly)
            ChannelMessageHandler handler = messageHandlerProvider.getIfAvailable();
            if (handler != null) {
                for (CamelChannelAdapter adapter : adapters.values()) {
                    if (!adapter.isRunning()) {
                        adapter.start(handler);
                        log.info("Started Camel channel adapter: {}", adapter.channelId());
                    }
                }
            } else {
                log.warn("No ChannelMessageHandler available — Camel adapters registered but not started");
            }

            // Phase 6: Print pipeline diagram
            if (!wiring.isEmpty()) {
                logPipelineDiagram(wiring);
            }
        };
    }

    private void registerAdapter(
            CamelChannelConfig config,
            CamelContext camelContext,
            ChannelRegistry channelRegistry,
            Map<String, CamelChannelAdapter> adapters) {
        if (adapters.containsKey(config.channelId()) || channelRegistry.contains(config.channelId())) {
            log.warn("Skipping Camel channel '{}' — already registered", config.channelId());
            return;
        }
        CamelChannelAdapter adapter = new CamelChannelAdapter(
                config, camelContext.createProducerTemplate(), camelContext);
        channelRegistry.register(adapter);
        adapters.put(config.channelId(), adapter);
        log.info("Registered Camel channel adapter: {} ({})", config.channelId(), config.displayName());
    }

    /**
     * Check if any existing route consumes from the given endpoint URI.
     */
    private boolean hasConsumerForEndpoint(CamelContext camelContext, String endpointUri) {
        String normalized = normalizeUri(endpointUri);
        for (Route route : camelContext.getRoutes()) {
            String routeUri = normalizeUri(route.getEndpoint().getEndpointUri());
            if (normalized.equals(routeUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalize a Camel URI for comparison: strip query params and ensure
     * the scheme separator is {@code ://} (Camel normalizes {@code seda:foo}
     * to {@code seda://foo}).
     */
    private static String normalizeUri(String uri) {
        int qIdx = uri.indexOf('?');
        String base = qIdx >= 0 ? uri.substring(0, qIdx) : uri;
        // Ensure scheme://path format for consistent comparison
        int colonIdx = base.indexOf(':');
        if (colonIdx > 0 && !base.startsWith("//", colonIdx + 1)) {
            base = base.substring(0, colonIdx) + "://" + base.substring(colonIdx + 1);
        }
        return base;
    }

    /**
     * Find a user-defined route that consumes from the given endpoint URI.
     * Returns "routeId (from-uri)" or "(unknown)" if not found.
     */
    private String findRouteConsumingFrom(CamelContext camelContext, String endpointUri) {
        String normalized = normalizeUri(endpointUri);
        for (Route route : camelContext.getRoutes()) {
            String routeUri = normalizeUri(route.getEndpoint().getEndpointUri());
            if (normalized.equals(routeUri)) {
                return route.getRouteId() + " (" + route.getEndpoint().getEndpointUri() + ")";
            }
        }
        return "(unknown)";
    }

    /**
     * Find a user-defined route that sends to the given endpoint URI by scanning
     * route definitions for matching {@code .to()} targets.
     * Returns "routeId (from-uri)" or "(unknown)" if not found.
     */
    private String findRouteTargeting(CamelContext camelContext, String targetUri) {
        String normalized = normalizeUri(targetUri);
        try {
            org.apache.camel.model.ModelCamelContext modelCtx =
                    camelContext.getCamelContextExtension().getContextPlugin(org.apache.camel.model.ModelCamelContext.class);
            if (modelCtx != null) {
                for (org.apache.camel.model.RouteDefinition routeDef : modelCtx.getRouteDefinitions()) {
                    for (org.apache.camel.model.ProcessorDefinition<?> proc : routeDef.getOutputs()) {
                        if (proc instanceof org.apache.camel.model.ToDefinition toDef) {
                            if (normalized.equals(normalizeUri(toDef.getUri()))) {
                                String fromUri = routeDef.getInput() != null ? routeDef.getInput().getUri() : "?";
                                return routeDef.getRouteId() + " (" + fromUri + ")";
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not inspect route definitions for diagram: {}", e.getMessage());
        }
        return "(unknown)";
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private void logPipelineDiagram(Map<String, String[]> wiring) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("============================================================\n");
        sb.append("  JaiClaw Camel Pipeline Wiring\n");
        sb.append("============================================================\n");

        for (Map.Entry<String, String[]> entry : wiring.entrySet()) {
            String channelId = entry.getKey();
            String inLabel = entry.getValue()[0];
            String outLabel = entry.getValue()[1];
            String sedaIn = "seda:jaiclaw-" + channelId + "-in";
            String sedaOut = "seda:jaiclaw-" + channelId + "-out";

            sb.append("\n");
            sb.append("  Channel: ").append(channelId).append("\n");
            sb.append("\n");
            sb.append("  [").append(inLabel).append("]").append("\n");
            sb.append("       |").append("\n");
            sb.append("       v").append("\n");
            sb.append("  ").append(sedaIn).append(" --> handler.onMessage() --> async agent\n");
            sb.append("                                                          |\n");
            sb.append("                         sendMessage(response) -----------+\n");
            sb.append("                                |\n");
            sb.append("                                v\n");
            sb.append("  ").append(sedaOut).append("\n");
            sb.append("       |").append("\n");
            sb.append("       v").append("\n");
            sb.append("  [").append(outLabel).append("]").append("\n");
        }

        sb.append("\n");
        sb.append("============================================================");
        log.info(sb.toString());
    }

}
