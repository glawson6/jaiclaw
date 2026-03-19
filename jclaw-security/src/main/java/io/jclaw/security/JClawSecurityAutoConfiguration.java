package io.jclaw.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Auto-configuration for JClaw security. Three modes:
 * <ul>
 *   <li>{@code api-key} (default) — auto-generated or explicit API key authentication</li>
 *   <li>{@code jwt} — JWT token authentication with role-based tool filtering</li>
 *   <li>{@code none} — permissive, no authentication (dev only)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
@EnableConfigurationProperties(JClawSecurityProperties.class)
public class JClawSecurityAutoConfiguration {

    /**
     * Always-active logger that reports the security mode at startup.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityModeLogger.class)
    public SecurityModeLogger securityModeLogger(JClawSecurityProperties properties,
                                                  ObjectProvider<ApiKeyProvider> apiKeyProvider) {
        return new SecurityModeLogger(properties, apiKeyProvider.getIfAvailable());
    }

    // ── API Key mode (default) ──────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "jclaw.security.mode", havingValue = "api-key", matchIfMissing = true)
    static class ApiKeySecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean(ApiKeyProvider.class)
        ApiKeyProvider apiKeyProvider(JClawSecurityProperties properties) {
            return new ApiKeyProvider(properties.apiKey(), properties.apiKeyFile());
        }

        @Bean
        @ConditionalOnMissingBean(ApiKeyAuthenticationFilter.class)
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider) {
            return new ApiKeyAuthenticationFilter(apiKeyProvider);
        }

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain apiKeyFilterChain(HttpSecurity http,
                                              ApiKeyAuthenticationFilter apiKeyFilter)
                throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/health").permitAll()
                            .requestMatchers("/webhook/**").permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .requestMatchers("/mcp/**").authenticated()
                            .anyRequest().permitAll()
                    )
                    .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    // ── JWT mode ────────────────────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "jclaw.security.mode", havingValue = "jwt")
    static class JwtSecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean(JwtTokenValidator.class)
        JwtTokenValidator jwtTokenValidator(JClawSecurityProperties properties) {
            JClawSecurityProperties.JwtProperties jwt = properties.jwt();
            if (jwt.secret() == null || jwt.secret().isBlank()) {
                throw new IllegalStateException(
                        "jclaw.security.jwt.secret must be set when jclaw.security.mode=jwt");
            }
            return new JwtTokenValidator(jwt.secret(), jwt.issuer(),
                    jwt.tenantClaim(), jwt.roleClaim());
        }

        @Bean
        @ConditionalOnMissingBean(RoleToolProfileResolver.class)
        RoleToolProfileResolver roleToolProfileResolver(JClawSecurityProperties properties) {
            JClawSecurityProperties.RoleMappingProperties mapping = properties.roleMapping();
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
        @ConditionalOnProperty(name = "jclaw.security.rate-limit.enabled", havingValue = "true")
        @ConditionalOnMissingBean(RateLimitFilter.class)
        RateLimitFilter rateLimitFilter(JClawSecurityProperties properties) {
            JClawSecurityProperties.RateLimitProperties rl = properties.rateLimit();
            return new RateLimitFilter(rl.maxRequestsPerWindow(), rl.windowSeconds(),
                    rl.cleanupIntervalSeconds());
        }

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain jwtFilterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           ObjectProvider<RateLimitFilter> rateLimitFilterProvider)
                throws Exception {
            HttpSecurity builder = http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/health").permitAll()
                            .requestMatchers("/webhook/**").permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .requestMatchers("/mcp/**").authenticated()
                            .anyRequest().permitAll()
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
    @ConditionalOnProperty(name = "jclaw.security.mode", havingValue = "none")
    static class NoneSecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain permissiveFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}
