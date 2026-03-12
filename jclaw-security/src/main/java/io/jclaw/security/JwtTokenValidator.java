package io.jclaw.security;

import io.jclaw.core.tenant.DefaultTenantContext;
import io.jclaw.core.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Validates JWT tokens and extracts tenant context and roles from claims.
 */
public class JwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);

    private final SecretKey signingKey;
    private final String tenantClaim;
    private final String roleClaim;
    private final String expectedIssuer;

    public JwtTokenValidator(String secret, String issuer, String tenantClaim, String roleClaim) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedIssuer = issuer;
        this.tenantClaim = tenantClaim;
        this.roleClaim = roleClaim;
    }

    /**
     * Validate the token and extract claims. Returns empty if validation fails.
     */
    public Optional<ValidatedToken> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Check issuer if configured
            if (expectedIssuer != null && !expectedIssuer.equals(claims.getIssuer())) {
                log.debug("JWT issuer mismatch: expected={}, actual={}", expectedIssuer, claims.getIssuer());
                return Optional.empty();
            }

            // Extract tenant
            String tenantId = claims.get(tenantClaim, String.class);
            if (tenantId == null) {
                // Fall back to "programId"
                tenantId = claims.get("programId", String.class);
            }
            if (tenantId == null || tenantId.isBlank()) {
                log.debug("JWT missing tenant claim: {}", tenantClaim);
                return Optional.empty();
            }

            String tenantName = claims.get("tenantName", String.class);
            if (tenantName == null) tenantName = tenantId;

            // Extract roles
            List<String> roles = extractRoles(claims);

            // Build metadata from additional claims
            Map<String, Object> metadata = new HashMap<>();
            String subject = claims.getSubject();
            if (subject != null) metadata.put("subject", subject);
            String staffId = claims.get("staffId", String.class);
            if (staffId != null) metadata.put("staffId", staffId);

            TenantContext tenantContext = new DefaultTenantContext(tenantId, tenantName, metadata);

            return Optional.of(new ValidatedToken(tenantContext, subject, roles));

        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        Object rolesObj = claims.get(roleClaim);
        if (rolesObj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        if (rolesObj instanceof String s) {
            return Arrays.asList(s.split(","));
        }
        return List.of();
    }

    /**
     * A validated JWT token with extracted tenant context, subject, and roles.
     */
    public record ValidatedToken(
            TenantContext tenantContext,
            String subject,
            List<String> roles
    ) {}
}
