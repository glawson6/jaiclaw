package io.jaiclaw.compliance.gdpr;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

/**
 * Holder bean referenced from {@code @PreAuthorize} SpEL expressions on
 * the {@link GdprController} Article 15 / 17 endpoints. Keeps the SpEL
 * string short + testable — each method returns {@code true} / {@code false}
 * for whether the current principal is authorised.
 *
 * <p>The role name comes from {@link GdprAuthzProperties.Roles#operator()}.
 * Bean is registered as {@code gdprAuthzExpressions} — that's the exact
 * name every {@code @PreAuthorize} expression uses.
 *
 * <p><b>Degraded-mode behaviour</b> (mirrors {@code PipelineAuthzExpressions}):
 * <ul>
 *   <li>If Spring Security is not on the classpath, the {@code @PreAuthorize}
 *       annotations are silently inert — this bean's methods are never
 *       invoked. The controller behaves as it did pre-1.1.0 (any authenticated
 *       principal passes).</li>
 *   <li>If Spring Security IS on the classpath but the role config value
 *       is blank, the check short-circuits to {@code true} — authenticated
 *       principals may invoke regardless of authority. Adopter escape hatch
 *       while designing the role hierarchy.</li>
 *   <li>If the role is set and no principal is authenticated, deny.</li>
 *   <li>If the role is set and the authenticated principal lacks the role,
 *       deny.</li>
 * </ul>
 */
public class GdprAuthzExpressions {

    private final GdprAuthzProperties.Roles roles;

    public GdprAuthzExpressions(GdprAuthzProperties.Roles roles) {
        this.roles = roles == null ? GdprAuthzProperties.Roles.defaults() : roles;
    }

    /** Authorises Article 15 (export) and Article 17 (erasure) endpoints. */
    public boolean operator() {
        return hasAuthorityOrBlank(roles.operator());
    }

    private static boolean hasAuthorityOrBlank(String authority) {
        if (authority == null || authority.isBlank()) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (Objects.equals(a.getAuthority(), authority)) return true;
        }
        return false;
    }
}
