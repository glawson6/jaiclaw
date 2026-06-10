package io.jaiclaw.autoconfigure;

import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.core.http.ProxyAwareHttpClientFactory;
import io.jaiclaw.core.http.ProxyAwareHttpClientFactory.ProxyConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;

/**
 * HTTP-client foundation auto-configuration.
 *
 * <p>Beans defined here:
 * <ul>
 *   <li>{@link RestClientCustomizer} — wires proxy settings into every
 *       Spring AI provider's {@code RestClient.Builder}.</li>
 *   <li>{@link ProxyFactoryConfigurer} — configures
 *       {@link ProxyAwareHttpClientFactory} for non-Spring HTTP clients
 *       (tools, MCP providers, etc.) and sets a global {@link Authenticator}
 *       for proxy auth credentials.</li>
 * </ul>
 *
 * <p>This auto-config runs <b>first</b> in the JaiClaw DAG — every downstream
 * bean that may make HTTP calls (Spring AI providers, channel adapters, web
 * tools) depends on the proxy configuration being in place.
 *
 * <p>Carved out of the former {@code JaiClawAutoConfiguration} monolith
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.4, Phase 3 P3.4).
 */
@AutoConfiguration
@EnableConfigurationProperties(JaiClawProperties.class)
public class JaiClawHttpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawHttpAutoConfiguration.class);

    /**
     * Resolve proxy config from explicit YAML properties or environment variables.
     * Returns null if no proxy is configured.
     */
    private static ProxyConfig resolveProxyConfig(JaiClawProperties properties) {
        var proxyProps = properties.http().proxy();
        if (proxyProps.isConfigured()) {
            return new ProxyConfig(proxyProps.host(), proxyProps.port(),
                    proxyProps.username(), proxyProps.password());
        }
        return ProxyAwareHttpClientFactory.resolveProxy();
    }

    /**
     * Customizes the Spring Boot {@link org.springframework.web.client.RestClient.Builder}
     * with proxy settings. This is picked up by Spring AI's provider auto-configurations
     * (Anthropic, OpenAI, Ollama) which receive a {@code RestClient.Builder} via
     * {@code ObjectProvider} — the builder is a prototype bean that gets customized
     * by all registered {@code RestClientCustomizer} beans before use.
     *
     * <p>Depends on {@link ProxyFactoryConfigurer} to ensure the static factory
     * is configured before this customizer runs.
     */
    @Bean
    RestClientCustomizer proxyRestClientCustomizer(ProxyFactoryConfigurer configurer) {
        return builder -> {
            ProxyConfig resolved = ProxyAwareHttpClientFactory.resolveProxy();
            if (resolved == null) return;

            // JdkClientHttpRequestFactory wraps a java.net.http.HttpClient for use
            // with Spring's RestClient — proxy and auth are baked into the HttpClient
            HttpClient proxyHttpClient = ProxyAwareHttpClientFactory.create();
            builder.requestFactory(new JdkClientHttpRequestFactory(proxyHttpClient));
        };
    }

    /**
     * Configures {@link ProxyAwareHttpClientFactory} for non-Spring HTTP clients
     * (tools, MCP providers, etc.) and sets a global {@link Authenticator} for
     * proxy auth if credentials are provided. Runs at bean construction time so
     * the factory is configured before any tool beans that use it eagerly.
     */
    @Bean
    ProxyFactoryConfigurer proxyFactoryConfigurer(JaiClawProperties properties) {
        return new ProxyFactoryConfigurer(properties);
    }

    /**
     * Configures {@link ProxyAwareHttpClientFactory} and global authenticator
     * as early as possible during bean creation.
     */
    static class ProxyFactoryConfigurer {
        ProxyFactoryConfigurer(JaiClawProperties properties) {
            ProxyConfig resolved = resolveProxyConfig(properties);
            if (resolved == null) return;

            ProxyAwareHttpClientFactory.configure(resolved);

            if (resolved.username() != null && !resolved.username().isBlank()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                resolved.username(),
                                resolved.password() != null ? resolved.password().toCharArray() : new char[0]);
                    }
                });
            }

            log.info("HTTP proxy configured: {}:{}", resolved.host(), resolved.port());
        }
    }
}
