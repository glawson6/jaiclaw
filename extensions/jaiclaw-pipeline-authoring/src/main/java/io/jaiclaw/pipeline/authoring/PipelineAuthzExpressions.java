package io.jaiclaw.pipeline.authoring;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

/**
 * Holder bean referenced from {@code @PreAuthorize} SpEL expressions
 * on the Phase 3 deployment endpoints. Keeps the SpEL string on each
 * annotation short and testable — each method just returns
 * {@code true} / {@code false} for whether the current principal is
 * authorised for that role.
 *
 * <p>The four Studio roles come from {@link PipelineAuthoringProperties.Roles}
 * config keys. Bean is registered as {@code pipelineAuthzExpressions} —
 * that's the exact name every {@code @PreAuthorize} expression uses.
 *
 * <p><b>Degraded-mode behaviour.</b> If Spring Security is not on the
 * classpath, none of these methods are ever invoked (the annotation
 * expressions themselves are inert — Spring silently skips them).
 * If Spring Security IS on the classpath but the role config value
 * is blank, the check short-circuits to {@code true} — i.e. authenticated
 * principals may invoke the endpoint regardless of authority. This is
 * intentional: it lets adopters roll out the module without immediately
 * having to design the role hierarchy.
 */
public class PipelineAuthzExpressions {

    private final PipelineAuthoringProperties.Roles roles;

    public PipelineAuthzExpressions(PipelineAuthoringProperties.Roles roles) {
        this.roles = roles == null ? PipelineAuthoringProperties.Roles.DEFAULT : roles;
    }

    public boolean viewer()   { return hasAuthorityOrBlank(roles.viewer()); }
    public boolean author()   { return hasAuthorityOrBlank(roles.author()); }
    public boolean deployer() { return hasAuthorityOrBlank(roles.deployer()); }
    public boolean runner()   { return hasAuthorityOrBlank(roles.runner()); }

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
