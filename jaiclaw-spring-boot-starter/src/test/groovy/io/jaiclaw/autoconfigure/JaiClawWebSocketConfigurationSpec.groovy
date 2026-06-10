package io.jaiclaw.autoconfigure

import io.jaiclaw.gateway.GatewayService
import io.jaiclaw.gateway.WebSocketSessionHandler
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import spock.lang.Specification

/**
 * 0.8.0: regression spec for the WebSocket handler-registration fix.
 *
 * <p>The e2e pre-release run for 0.8.0 caught that
 * {@link WebSocketSessionHandler} was created as a bean but never
 * registered against any URL path — the documented
 * {@code /ws/session/{sessionKey}} endpoint silently 404'd. Fix added a
 * nested {@code JaiClawWebSocketConfiguration} on
 * {@link JaiClawGatewayAutoConfiguration}.
 *
 * <p>This spec verifies the path + allowed-origin defaults and the two
 * {@code jaiclaw.gateway.websocket.*} property overrides.
 */
class JaiClawWebSocketConfigurationSpec extends Specification {

    private WebSocketSessionHandler stubHandler() {
        return new WebSocketSessionHandler(Stub(GatewayService))
    }

    def "default path is /ws/session/** and allowed-origin-patterns is *"() {
        given:
        WebSocketSessionHandler handler = stubHandler()
        JaiClawGatewayAutoConfiguration.JaiClawWebSocketConfiguration cfg =
                new JaiClawGatewayAutoConfiguration.JaiClawWebSocketConfiguration(
                        handler, "/ws/session/**", ["*"] as String[])
        RecordingRegistry registry = new RecordingRegistry()

        when:
        cfg.registerWebSocketHandlers(registry)

        then:
        registry.lastPath == "/ws/session/**"
        registry.lastHandler.is(handler)
        registry.lastOriginPatterns == ["*"] as String[]
    }

    def "custom path via jaiclaw.gateway.websocket.path is honored"() {
        given:
        WebSocketSessionHandler handler = stubHandler()
        JaiClawGatewayAutoConfiguration.JaiClawWebSocketConfiguration cfg =
                new JaiClawGatewayAutoConfiguration.JaiClawWebSocketConfiguration(
                        handler, "/agent/ws/**", ["*"] as String[])
        RecordingRegistry registry = new RecordingRegistry()

        when:
        cfg.registerWebSocketHandlers(registry)

        then:
        registry.lastPath == "/agent/ws/**"
    }

    def "custom allowed-origin-patterns are honored"() {
        given:
        WebSocketSessionHandler handler = stubHandler()
        String[] origins = ["https://app.example.test", "https://admin.example.test"]
        JaiClawGatewayAutoConfiguration.JaiClawWebSocketConfiguration cfg =
                new JaiClawGatewayAutoConfiguration.JaiClawWebSocketConfiguration(
                        handler, "/ws/session/**", origins)
        RecordingRegistry registry = new RecordingRegistry()

        when:
        cfg.registerWebSocketHandlers(registry)

        then:
        registry.lastOriginPatterns == origins
    }

    /** Minimal {@link WebSocketHandlerRegistry} that captures the last registration call. */
    private static class RecordingRegistry implements WebSocketHandlerRegistry {
        Object lastHandler
        String lastPath
        String[] lastOriginPatterns

        @Override
        WebSocketHandlerRegistration addHandler(
                org.springframework.web.socket.WebSocketHandler handler, String... paths) {
            this.lastHandler = handler
            this.lastPath = paths[0]
            return new RecordingRegistration(this)
        }
    }

    private static class RecordingRegistration implements WebSocketHandlerRegistration {
        final RecordingRegistry parent

        RecordingRegistration(RecordingRegistry parent) { this.parent = parent }

        @Override
        WebSocketHandlerRegistration addHandler(
                org.springframework.web.socket.WebSocketHandler handler, String... paths) { return this }

        @Override
        WebSocketHandlerRegistration setHandshakeHandler(
                org.springframework.web.socket.server.HandshakeHandler handshakeHandler) { return this }

        @Override
        WebSocketHandlerRegistration addInterceptors(
                org.springframework.web.socket.server.HandshakeInterceptor... interceptors) { return this }

        @Override
        WebSocketHandlerRegistration setAllowedOrigins(String... origins) { return this }

        @Override
        WebSocketHandlerRegistration setAllowedOriginPatterns(String... patterns) {
            parent.lastOriginPatterns = patterns
            return this
        }

        @Override
        org.springframework.web.socket.config.annotation.SockJsServiceRegistration withSockJS() { return null }
    }
}
