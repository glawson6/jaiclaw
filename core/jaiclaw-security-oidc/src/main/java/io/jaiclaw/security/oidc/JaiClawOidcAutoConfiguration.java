package io.jaiclaw.security.oidc;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import io.jaiclaw.security.RateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-configuration for {@code jaiclaw.security.mode=oidc}.
 *
 * <p>Adds a fourth authentication mode alongside {@code api-key}, {@code jwt}
 * and {@code none}. Entirely provider-agnostic — every provider-specific value
 * is configuration. See {@code docs/user/OIDC-AUTHENTICATION.md} for worked
 * examples against several identity providers.
 *
 * <p>1.3.0.
 */
@AutoConfiguration
@ConditionalOnClass(name = {
        "org.springframework.security.oauth2.jwt.JwtDecoder",
        "org.springframework.security.web.SecurityFilterChain"
})
@ConditionalOnProperty(name = "jaiclaw.security.mode", havingValue = "oidc")
@EnableConfigurationProperties(OidcProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class JaiClawOidcAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawOidcAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ScopeToolProfileMapper.class)
    public ScopeToolProfileMapper scopeToolProfileMapper(OidcProperties properties) {
        ScopeToolProfileMapper mapper = new ScopeToolProfileMapper(properties.scopeToProfile());
        if (mapper.isEmpty()) {
            log.info("jaiclaw.security.oidc.scope-to-profile is empty — every authenticated "
                    + "caller falls back to jaiclaw.security.default-tool-profile. Map scopes "
                    + "to profiles to vary tool access by caller.");
        }
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jaiClawJwtDecoder(OidcProperties properties) {
        if (properties.issuerUri() == null) {
            throw new IllegalStateException(
                    "jaiclaw.security.oidc.issuer-uri must be set when "
                            + "jaiclaw.security.mode=oidc.");
        }
        String jwkSetUri = properties.resolvedJwkSetUri();

        if (properties.audience() == null) {
            // Not fatal — some single-service deployments legitimately have no
            // distinct resource indicator — but it is a real weakening, so it is
            // said once, loudly, rather than silently accepted.
            log.warn("jaiclaw.security.oidc.audience is not set. Tokens this issuer minted for "
                    + "OTHER services will be accepted here. Set it to this deployment's "
                    + "resource indicator.");
        }

        List<SignatureAlgorithm> algorithms = new ArrayList<>();
        for (String alg : properties.jwsAlgorithms()) {
            // SignatureAlgorithm covers only the asymmetric family (RS*, PS*, ES*),
            // so an HMAC or 'none' entry simply fails to resolve. That is the
            // desired outcome — a resource server validating against a PUBLIC JWKS
            // must never accept a symmetric algorithm, because anyone who can read
            // the public key could then sign their own tokens with it — but the
            // default message would be cryptic, so it is restated here.
            SignatureAlgorithm parsed = SignatureAlgorithm.from(alg);
            if (parsed == null) {
                throw new IllegalStateException(
                        "jaiclaw.security.oidc.jws-algorithms contains '" + alg + "', which is "
                                + "not an asymmetric JWS algorithm. Valid values: RS256, RS384, "
                                + "RS512, PS256, PS384, PS512, ES256, ES384, ES512. Symmetric "
                                + "(HS*) and unsigned ('none') algorithms are refused by design.");
            }
            algorithms.add(parsed);
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .jwsAlgorithms(set -> set.addAll(algorithms))
                // RFC 9068 access tokens carry `typ: at+jwt`, which Spring rejects
                // by default. Note there are TWO independent checks: this
                // JOSE-level verifier, and a JwtTypeValidator in the default
                // validator chain. Both accept only `JWT` out of the box, and
                // both must be dealt with — the setJwtValidator call below
                // replaces the chain wholesale, which handles the second.
                // Configure only one and tokens still fail, with an error message
                // that names `typ` either way and gives no hint which check fired.
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<SecurityContext>(
                                properties.acceptedTypes().stream()
                                        .map(JOSEObjectType::new)
                                        .toArray(JOSEObjectType[]::new))))
                .build();

        // Replaces the DEFAULT chain, which includes a JwtTypeValidator accepting
        // only `typ: JWT`. Retaining it would reject every RFC 9068 access token
        // regardless of the JOSE type verifier configured above.
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator(properties.issuerUri()));
        validators.add(new AudienceValidator(properties.audience()));
        if (properties.requiredScope() != null) {
            validators.add(new RequiredScopeValidator(properties.requiredScope()));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));

        log.info("OIDC resource server configured: issuer={}, jwks={}, audience={}, algorithms={}",
                properties.issuerUri(), jwkSetUri,
                properties.audience() == null ? "(none — see warning above)" : properties.audience(),
                properties.jwsAlgorithms());
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean(JaiClawJwtAuthenticationConverter.class)
    public JaiClawJwtAuthenticationConverter jaiClawJwtAuthenticationConverter(
            OidcProperties properties, ScopeToolProfileMapper mapper) {
        return new JaiClawJwtAuthenticationConverter(properties, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain oidcFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JaiClawJwtAuthenticationConverter converter,
            ObjectProvider<RateLimitFilter> rateLimitFilterProvider,
            ObjectProvider<OidcPrincipalContextFilter> contextFilterProvider) throws Exception {

        http.headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000)));

        // Matcher set is deliberately identical to the api-key and jwt chains:
        // this mode changes HOW callers authenticate, not WHAT is protected.
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        // RFC 9728 discovery — unauthenticated by necessity.
                        .requestMatchers("/.well-known/oauth-protected-resource").permitAll()
                        // OAuth redirect target for channel linking — browser-reached,
                        // protected by the single-use OAuth state.
                        .requestMatchers("/api/identity/link/callback").permitAll()
                        .requestMatchers("/webhook/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/mcp/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(converter)));

        OidcPrincipalContextFilter contextFilter = contextFilterProvider.getIfAvailable();
        if (contextFilter != null) {
            http.addFilterAfter(contextFilter,
                    org.springframework.security.oauth2.server.resource.web.authentication
                            .BearerTokenAuthenticationFilter.class);
        }

        RateLimitFilter rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
        if (rateLimitFilter != null) {
            http.addFilterAfter(rateLimitFilter,
                    org.springframework.security.oauth2.server.resource.web.authentication
                            .BearerTokenAuthenticationFilter.class);
        }

        return http.build();
    }

    /**
     * Populates the thread-local holders from the validated principal.
     *
     * <p>Spring Security sets the {@code SecurityContext}, but a good deal of
     * JaiClaw reads {@code TenantContextHolder} / {@code ToolProfileHolder}
     * directly. This filter bridges the two and clears both afterwards.
     */
    @Bean
    @ConditionalOnMissingBean(OidcPrincipalContextFilter.class)
    public OidcPrincipalContextFilter oidcPrincipalContextFilter() {
        return new OidcPrincipalContextFilter();
    }
}
