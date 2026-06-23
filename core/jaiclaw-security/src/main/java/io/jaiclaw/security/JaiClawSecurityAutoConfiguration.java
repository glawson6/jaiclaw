package io.jaiclaw.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.Set;

/**
 * Auto-configuration for JaiClaw security. Three modes:
 * <ul>
 *   <li>{@code api-key} (default) — auto-generated or explicit API key authentication</li>
 *   <li>{@code jwt} — JWT token authentication with role-based tool filtering</li>
 *   <li>{@code none} — permissive, no authentication (dev only)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
@EnableConfigurationProperties(JaiClawSecurityProperties.class)
public class JaiClawSecurityAutoConfiguration {

    /**
     * Always-active logger that reports the security mode at startup.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityModeLogger.class)
    public SecurityModeLogger securityModeLogger(JaiClawSecurityProperties properties,
                                                  ObjectProvider<ApiKeyProvider> apiKeyProvider) {
        return new SecurityModeLogger(properties, apiKeyProvider.getIfAvailable());
    }

    /**
     * Rate limit filter — shared across API key and JWT modes.
     */
    @Bean
    @ConditionalOnProperty(name = "jaiclaw.security.rate-limit.enabled", havingValue = "true")
    @ConditionalOnMissingBean(RateLimitFilter.class)
    RateLimitFilter rateLimitFilter(JaiClawSecurityProperties properties) {
        JaiClawSecurityProperties.RateLimitProperties rl = properties.rateLimit();
        return new RateLimitFilter(rl.maxRequestsPerWindow(), rl.windowSeconds(),
                rl.cleanupIntervalSeconds());
    }

    /**
     * Applies standard security response headers to all filter chains.
     */
    private static void configureSecurityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000))
        );
    }

    // ── API Key mode (default) ──────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "jaiclaw.security.mode", havingValue = "api-key", matchIfMissing = true)
    static class ApiKeySecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean(ApiKeyProvider.class)
        ApiKeyProvider apiKeyProvider(JaiClawSecurityProperties properties) {
            return new ApiKeyProvider(properties.apiKey(), properties.apiKeyFile());
        }

        @Bean
        @ConditionalOnMissingBean(ApiKeyAuthenticationFilter.class)
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider,
                                                              JaiClawSecurityProperties properties) {
            return new ApiKeyAuthenticationFilter(apiKeyProvider, null, properties.timingSafeApiKey());
        }

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain apiKeyFilterChain(HttpSecurity http,
                                              ApiKeyAuthenticationFilter apiKeyFilter,
                                              ObjectProvider<RateLimitFilter> rateLimitFilterProvider)
                throws Exception {
            configureSecurityHeaders(http);
            HttpSecurity builder = http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/health").permitAll()
                            .requestMatchers("/webhook/**").permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .requestMatchers("/mcp/**").authenticated()
                            .anyRequest().denyAll()
                    )
                    .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

            RateLimitFilter rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
            if (rateLimitFilter != null) {
                builder.addFilterAfter(rateLimitFilter, ApiKeyAuthenticationFilter.class);
            }

            return builder.build();
        }
    }

    // ── JWT mode ────────────────────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "jaiclaw.security.mode", havingValue = "jwt")
    static class JwtSecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean(JwtTokenValidator.class)
        JwtTokenValidator jwtTokenValidator(JaiClawSecurityProperties properties) {
            JaiClawSecurityProperties.JwtProperties jwt = properties.jwt();
            if (jwt.secret() == null || jwt.secret().isBlank()) {
                throw new IllegalStateException(
                        "jaiclaw.security.jwt.secret must be set when jaiclaw.security.mode=jwt");
            }
            if (jwt.secret().length() < 32) {
                throw new IllegalStateException(
                        "jaiclaw.security.jwt.secret must be at least 32 characters (256 bits) for HMAC-SHA256. "
                                + "Current length: " + jwt.secret().length());
            }
            return new JwtTokenValidator(jwt.secret(), jwt.issuer(),
                    jwt.tenantClaim(), jwt.roleClaim());
        }

        @Bean
        @ConditionalOnMissingBean(RoleToolProfileResolver.class)
        RoleToolProfileResolver roleToolProfileResolver(JaiClawSecurityProperties properties) {
            JaiClawSecurityProperties.RoleMappingProperties mapping = properties.roleMapping();
            return new RoleToolProfileResolver(mapping.roleToProfile(), mapping.defaultProfile());
        }

        @Bean
        @ConditionalOnMissingBean(JwtAuthenticationFilter.class)
        JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtTokenValidator validator,
                ObjectProvider<RoleToolProfileResolver> resolverProvider) {
            return new JwtAuthenticationFilter(validator, resolverProvider.getIfAvailable());
        }

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain jwtFilterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           ObjectProvider<RateLimitFilter> rateLimitFilterProvider)
                throws Exception {
            configureSecurityHeaders(http);
            HttpSecurity builder = http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/health").permitAll()
                            .requestMatchers("/webhook/**").permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .requestMatchers("/mcp/**").authenticated()
                            .anyRequest().denyAll()
                    )
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

            RateLimitFilter rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
            if (rateLimitFilter != null) {
                builder.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
            }

            return builder.build();
        }
    }

    // ── None mode (permissive) ──────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "jaiclaw.security.mode", havingValue = "none")
    static class NoneSecurityConfiguration {

        private static final Logger log = LoggerFactory.getLogger(NoneSecurityConfiguration.class);

        /**
         * Loopback bind addresses. {@code mode=none} is permitted on
         * any of these without the waiver flag because traffic is
         * already constrained to the local machine.
         *
         * <p>Empty string is included because Spring Boot's default
         * {@code server.address} is empty, which binds to all
         * interfaces. We treat that as public — it is, on multi-NIC
         * machines and containers — and require the waiver.
         */
        private static final Set<String> LOOPBACK_BINDS = Set.of(
                "127.0.0.1", "localhost", "::1", "0:0:0:0:0:0:0:1");

        private final Environment env;
        private final JaiClawSecurityProperties props;

        NoneSecurityConfiguration(Environment env, JaiClawSecurityProperties props) {
            this.env = env;
            this.props = props;
        }

        /**
         * Fail-fast guard: {@code mode=none} on a non-localhost bind
         * is almost always an operator footgun. We refuse to start
         * unless {@code jaiclaw.security.allow-none-on-public-bind=true}
         * is explicitly set as a waiver.
         *
         * <p>Added in 0.9.2 — see docs/dev/RELEASE-PLAN-0.9.2.md §5.
         */
        @PostConstruct
        void verifyBindAddress() {
            String bind = env.getProperty("server.address", "").trim();
            if (LOOPBACK_BINDS.contains(bind)) {
                log.info("jaiclaw.security.mode=none on loopback bind '{}' — no auth, no guard required",
                        bind.isEmpty() ? "127.0.0.1" : bind);
                return;
            }
            if (props.allowNoneOnPublicBind()) {
                log.warn("jaiclaw.security.mode=none on non-loopback bind '{}' — running unauthenticated "
                        + "because jaiclaw.security.allow-none-on-public-bind=true. THIS IS DANGEROUS.",
                        bind.isEmpty() ? "all interfaces" : bind);
                return;
            }
            throw new IllegalStateException(
                    "Refusing to start: jaiclaw.security.mode=none on bind '"
                            + (bind.isEmpty() ? "all interfaces (default)" : bind) + "'. "
                            + "Unauthenticated access on a non-loopback bind is almost certainly a "
                            + "configuration error. Either:\n"
                            + "  - Set jaiclaw.security.mode=api-key (or jwt) for production, OR\n"
                            + "  - Set server.address=127.0.0.1 to constrain to localhost, OR\n"
                            + "  - If you really mean it, set "
                            + "jaiclaw.security.allow-none-on-public-bind=true.");
        }

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain permissiveFilterChain(HttpSecurity http) throws Exception {
            configureSecurityHeaders(http);
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}
