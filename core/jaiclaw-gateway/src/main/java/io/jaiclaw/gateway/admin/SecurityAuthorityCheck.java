package io.jaiclaw.gateway.admin;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

/**
 * Authority check isolated in its own class so the caller can catch
 * {@link NoClassDefFoundError} when Spring Security is absent.
 *
 * <p>Separating it matters: if the Spring Security types were referenced
 * directly from {@code EstopActuatorEndpoint}, classloading that endpoint would
 * fail outright on a classpath without Spring Security, rather than letting the
 * endpoint load and deny.
 */
final class SecurityAuthorityCheck {

    private SecurityAuthorityCheck() {
    }

    /** True when an authenticated principal holds {@code authority}. */
    static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (Objects.equals(granted.getAuthority(), authority)) {
                return true;
            }
        }
        return false;
    }
}
