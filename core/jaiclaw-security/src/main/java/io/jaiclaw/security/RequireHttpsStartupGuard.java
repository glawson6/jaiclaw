package io.jaiclaw.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * T1-7: refuse to start on a non-loopback bind address when TLS is not
 * configured and {@code jaiclaw.security.require-https=true}.
 *
 * <p>Motivation: GDPR Art. 32 and HIPAA §164.312(e) require transmission
 * encryption. The framework can't force TLS termination — that's the
 * reverse proxy's job — but it can refuse to start on a public bind
 * without it, giving deployers a loud, early signal instead of silent
 * plaintext PHI on the wire.
 *
 * <p>Default behavior (opt-in): {@code jaiclaw.security.require-https=false}
 * → guard is a no-op. This preserves dev-workflow ergonomics (localhost
 * HTTP is still fine). Set the property to {@code true} in production
 * profiles to enforce.
 *
 * <p>The guard checks three things:
 * <ol>
 *   <li>{@code server.ssl.enabled} — if true, all good.</li>
 *   <li>{@code server.address} — if loopback (127.0.0.1, ::1, localhost, or
 *       unset which Spring interprets as loopback for dev), all good.</li>
 *   <li>Otherwise → throw {@link IllegalStateException} at startup.</li>
 * </ol>
 *
 * <p>Wired as a bean by {@code JaiClawSecurityAutoConfiguration}. Fires on
 * {@code afterPropertiesSet} so failure aborts startup before any request
 * hits the wire.
 */
public class RequireHttpsStartupGuard {

    private static final Logger log = LoggerFactory.getLogger(RequireHttpsStartupGuard.class);

    private final Environment env;
    private final JaiClawSecurityProperties props;

    public RequireHttpsStartupGuard(Environment env, JaiClawSecurityProperties props) {
        this.env = env;
        this.props = props;
    }

    /**
     * Called during Spring bean initialization. Throws if the deployment
     * would ship plaintext data over the wire and the guard is enabled.
     */
    public void enforce() {
        if (!props.requireHttps()) {
            return;   // opt-in; default false
        }
        if (sslEnabled()) {
            log.info("HTTPS enforcement: TLS enabled — guard satisfied");
            return;
        }
        if (isLoopbackBind()) {
            log.info("HTTPS enforcement: bound to loopback address — guard satisfied (dev workflow)");
            return;
        }
        String bind = boundAddress();
        throw new IllegalStateException(
                "jaiclaw.security.require-https=true but the server is bound to "
                        + bind + " without TLS. Refusing to start — configure server.ssl.* or "
                        + "bind to localhost. This guard protects against plaintext PHI / personal "
                        + "data over the wire (GDPR Art. 32, HIPAA §164.312(e)).");
    }

    private boolean sslEnabled() {
        // Spring Boot's server.ssl.enabled defaults to false but is treated
        // as true if a keystore is configured. Check both signals.
        String enabled = env.getProperty("server.ssl.enabled");
        if ("true".equalsIgnoreCase(enabled)) return true;
        String keyStore = env.getProperty("server.ssl.key-store");
        return keyStore != null && !keyStore.isBlank();
    }

    private boolean isLoopbackBind() {
        String addr = boundAddress();
        if (addr == null || addr.isBlank()) {
            // Spring's default when server.address is unset is to bind to
            // all interfaces (0.0.0.0). That's NOT loopback — treat as public.
            return false;
        }
        String norm = addr.trim().toLowerCase();
        return "127.0.0.1".equals(norm)
                || "::1".equals(norm)
                || "0:0:0:0:0:0:0:1".equals(norm)
                || "localhost".equals(norm);
    }

    private String boundAddress() {
        return env.getProperty("server.address");
    }
}
