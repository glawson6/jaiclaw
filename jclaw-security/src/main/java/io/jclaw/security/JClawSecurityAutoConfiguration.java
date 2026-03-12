package io.jclaw.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Auto-configuration for JClaw JWT security.
 * Activated when {@code jclaw.security.enabled=true} and Spring Security is on the classpath.
 * <p>
 * When disabled (the default), a permissive filter chain is configured that allows
 * all requests — this preserves backward compatibility for single-tenant/dev setups.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
@EnableConfigurationProperties(JClawSecurityProperties.class)
public class JClawSecurityAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "jclaw.security.enabled", havingValue = "true")
    @ConditionalOnMissingBean(JwtTokenValidator.class)
    public JwtTokenValidator jwtTokenValidator(JClawSecurityProperties properties) {
        JClawSecurityProperties.JwtProperties jwt = properties.jwt();
        if (jwt.secret() == null || jwt.secret().isBlank()) {
            throw new IllegalStateException(
                    "jclaw.security.jwt.secret must be set when jclaw.security.enabled=true");
        }
        return new JwtTokenValidator(jwt.secret(), jwt.issuer(), jwt.tenantClaim(), jwt.roleClaim());
    }

    @Bean
    @ConditionalOnProperty(name = "jclaw.security.enabled", havingValue = "true")
    @ConditionalOnMissingBean(JwtAuthenticationFilter.class)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator validator) {
        return new JwtAuthenticationFilter(validator);
    }

    /**
     * Secured filter chain — active when jclaw.security.enabled=true.
     * Requires JWT auth on /api/** endpoints, permits health and webhook endpoints.
     */
    @Bean
    @ConditionalOnProperty(name = "jclaw.security.enabled", havingValue = "true")
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securedFilterChain(HttpSecurity http,
                                                  JwtAuthenticationFilter jwtFilter)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/webhook/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Permissive filter chain — active when security is disabled (default).
     * Allows all requests, no authentication required.
     */
    @Bean
    @ConditionalOnProperty(name = "jclaw.security.enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain permissiveFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
