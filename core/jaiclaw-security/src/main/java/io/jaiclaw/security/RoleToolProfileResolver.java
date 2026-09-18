package io.jaiclaw.security;

import io.jaiclaw.core.tool.ToolProfile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps JWT roles to {@link ToolProfile} values.
 * When a user has multiple roles, the highest-privilege profile wins.
 *
 * <p>Ranking uses {@link ToolProfile#privilege()}, not {@code ordinal()}. The
 * two disagree: declaration order puts {@code CODING} before {@code MESSAGING},
 * while the privilege lattice ranks {@code MESSAGING} below {@code CODING}
 * (sending a message is narrower than shell and filesystem access). Ranking by
 * ordinal therefore handed a user holding both roles the weaker profile.
 *
 * <p>Hierarchy: NONE &lt; MINIMAL &lt; WEBHOOK_SAFE &lt; MESSAGING &lt; CODING &lt; FULL.
 */
public class RoleToolProfileResolver {

    private final Map<String, ToolProfile> roleToProfile;
    private final ToolProfile defaultProfile;

    public RoleToolProfileResolver(Map<String, String> roleToProfileStrings, String defaultProfileString) {
        this.roleToProfile = roleToProfileStrings.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> ToolProfile.valueOf(e.getValue().toUpperCase())));
        this.defaultProfile = ToolProfile.valueOf(defaultProfileString.toUpperCase());
    }

    /**
     * Resolve the highest-privilege {@link ToolProfile} for the given roles.
     * Falls back to {@code defaultProfile} if no role matches.
     */
    public ToolProfile resolve(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return defaultProfile;
        }

        ToolProfile best = null;
        for (String role : roles) {
            ToolProfile mapped = roleToProfile.get(role);
            if (mapped != null && (best == null || mapped.privilege() > best.privilege())) {
                best = mapped;
            }
        }

        return best != null ? best : defaultProfile;
    }
}
