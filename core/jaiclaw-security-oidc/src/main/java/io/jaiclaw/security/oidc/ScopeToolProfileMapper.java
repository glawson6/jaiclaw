package io.jaiclaw.security.oidc;

import io.jaiclaw.core.tool.ToolProfile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps OAuth scopes to a {@link ToolProfile}.
 *
 * <p>Scopes rather than roles, deliberately. Roles are commonly an ID-token or
 * userinfo claim and are frequently absent from access tokens altogether — the
 * space-delimited {@code scope} claim is the one authorization signal an OAuth
 * resource server can rely on. Deployments that would rather drive this from
 * roles should have their provider project those roles into scopes (most
 * support a claim-mapping or token-customizer hook) rather than have JaiClaw
 * call userinfo on the hot path.
 *
 * <p>When a caller holds several mapped scopes the <strong>highest privilege
 * wins</strong>, ranked by {@link ToolProfile#privilege()} — not
 * {@code ordinal()}, which encodes declaration order and disagrees.
 *
 * <p>1.3.0.
 */
public class ScopeToolProfileMapper {

    private final Map<String, ToolProfile> scopeToProfile;

    public ScopeToolProfileMapper(Map<String, String> configured) {
        Map<String, ToolProfile> parsed = new LinkedHashMap<>();
        if (configured != null) {
            configured.forEach((scope, profile) -> {
                try {
                    parsed.put(scope, ToolProfile.valueOf(profile.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException(
                            "Invalid jaiclaw.security.oidc.scope-to-profile value '" + profile
                                    + "' for scope '" + scope + "'. Valid profiles: NONE, MINIMAL, "
                                    + "WEBHOOK_SAFE, MESSAGING, CODING, FULL.", e);
                }
            });
        }
        this.scopeToProfile = Map.copyOf(parsed);
    }

    /**
     * The most privileged profile among the caller's scopes.
     *
     * @return the mapped profile, or {@code null} when no scope matches — the
     *         caller then falls back to
     *         {@code jaiclaw.security.default-tool-profile}
     */
    public ToolProfile map(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty() || scopeToProfile.isEmpty()) {
            return null;
        }
        ToolProfile best = null;
        for (String scope : scopes) {
            ToolProfile mapped = scopeToProfile.get(scope);
            if (mapped != null && (best == null || mapped.privilege() > best.privilege())) {
                best = mapped;
            }
        }
        return best;
    }

    /** Whether any scope mapping is configured. */
    public boolean isEmpty() {
        return scopeToProfile.isEmpty();
    }
}
