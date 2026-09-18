package io.jaiclaw.security.actuator;

import io.jaiclaw.security.ApiKeyEntry;
import io.jaiclaw.security.ApiKeyStore;
import io.jaiclaw.security.JaiClawSecurityProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of the configured API keys, for diagnosing 401s and 403s.
 *
 * <p>Reports each key's <em>name</em>, tenant, and role — <strong>never key
 * material, and never a masked form of it</strong>. Nothing here helps an
 * attacker authenticate; it answers "which keys does this deployment know
 * about, and what is each bound to", which is exactly the question an operator
 * staring at a 403 needs answered.
 *
 * <p>Like every other actuator endpoint in this codebase, it performs no
 * authorization of its own. Adopters must front {@code /actuator/**} with the
 * same authentication they use for the rest of their admin surface.
 *
 * <p>Exposure is opt-in as usual:
 * {@code management.endpoints.web.exposure.include: jaiclaw-api-keys,health}.
 *
 * <p>1.2.0.
 */
@Endpoint(id = "jaiclaw-api-keys")
public class ApiKeyActuatorEndpoint {

    private final JaiClawSecurityProperties properties;
    private final ApiKeyStore store;

    public ApiKeyActuatorEndpoint(JaiClawSecurityProperties properties, ApiKeyStore store) {
        this.properties = properties;
        this.store = store;
    }

    @ReadOperation
    public Map<String, Object> keys() {
        List<ApiKeyEntry> entries = properties.apiKeys();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", properties.mode());
        out.put("count", store != null && store.size() >= 0 ? store.size() : entries.size());
        out.put("defaultToolProfile", properties.defaultToolProfile());
        out.put("keys", entries.stream().map(ApiKeyActuatorEndpoint::summarize).toList());
        return out;
    }

    private static Map<String, Object> summarize(ApiKeyEntry entry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", entry.name());
        m.put("tenantId", entry.tenantId() == null ? "(none)" : entry.tenantId());
        m.put("role", entry.role() == null ? "(none)" : entry.role());
        m.put("source", entry.secretRef() != null ? "secret-ref" : "config");
        return m;
    }
}
