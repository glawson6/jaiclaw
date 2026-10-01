package io.jaiclaw.gateway.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exposure and authorization for the emergency-stop actuator endpoint.
 *
 * <pre>{@code
 * jaiclaw:
 *   gateway:
 *     admin:
 *       estop:
 *         endpoint-enabled: true        # default false
 *         role: JAICLAW_OPERATOR        # required to allow the write operation
 * }</pre>
 *
 * <h2>Why the endpoint is off by default</h2>
 *
 * <p>{@code POST /actuator/jaiclaw-estop} pauses every agent in the deployment,
 * and a bare POST with no body engages the stop. The endpoint performs no
 * authorization of its own, and in {@code jaiclaw.security.mode=none} the filter
 * chain is {@code anyRequest().permitAll()} — so exposing it there publishes a
 * fleet-wide kill switch to anyone who can reach the port.
 *
 * <p>The emergency stop does not need this endpoint. It is a file sentinel, so
 * {@code bin/jaiclaw pause} works with no JVM and no HTTP surface at all, and
 * keeps working when the app is wedged. The endpoint is the convenience, not the
 * mechanism.
 *
 * <h2>Why a blank role denies here</h2>
 *
 * <p>Unlike {@link AdminAuthzProperties}, a blank role on this endpoint means
 * <strong>deny</strong>, not "any authenticated principal". Admin's
 * blank-is-allow default exists for backward compatibility with api-key
 * deployments whose principals carry no authorities, and it is listed in
 * {@code SECURITY.md} as a known weak default. A newly introduced control should
 * not inherit that: an operator who turns this endpoint on without naming a role
 * gets a 403 and a startup warning telling them which property to set, rather
 * than an open kill switch.
 *
 * <p>One public constructor per {@code @ConfigurationProperties} record
 * (Boot 4 record-binder rule); programmatic defaults live on {@link #defaults()}.
 */
@ConfigurationProperties(prefix = "jaiclaw.gateway.admin.estop")
public record EstopAuthzProperties(
        boolean endpointEnabled,
        String role
) {

    public EstopAuthzProperties {
        if (role == null) role = "";
    }

    /** Programmatic default — endpoint off, no role. The binder never sees this. */
    public static EstopAuthzProperties defaults() {
        return new EstopAuthzProperties(false, "");
    }

    /** True when a role is configured, i.e. the write operation can be authorised. */
    public boolean hasRole() {
        return !role.isBlank();
    }
}
