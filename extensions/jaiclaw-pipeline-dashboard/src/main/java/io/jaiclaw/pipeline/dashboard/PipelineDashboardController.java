package io.jaiclaw.pipeline.dashboard;

import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves the static Pipeline Dashboard page + a tiny whoami endpoint
 * that returns the current tenant id so the SPA can decide whether to
 * show the tenant switcher.
 *
 * <p>All dashboard assets live under {@code classpath:jaiclaw-pipeline-dashboard/}
 * and are exposed via a resource-handler registered in
 * {@link PipelineDashboardAutoConfiguration}. This controller only
 * handles the two dynamic endpoints ({@code /pipelines/dashboard} → HTML
 * shell, {@code /pipelines/dashboard/whoami} → tenant JSON).
 */
@RestController
public class PipelineDashboardController {

    private final Resource dashboardHtml = new ClassPathResource(
            "jaiclaw-pipeline-dashboard/dashboard.html");

    /**
     * Serves the dashboard HTML shell. Kept as a controller rather than
     * a plain resource handler so we can add server-side context injection
     * later (currently just returns the static bytes).
     */
    @GetMapping(value = "/pipelines/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> dashboard() {
        if (!dashboardHtml.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(dashboardHtml);
    }

    /**
     * Returns the current tenant context. Consumed by the dashboard on
     * page load so the tenant switcher UI is only rendered when a
     * multi-tenant deployment has an active tenant on the request.
     */
    @GetMapping(value = "/pipelines/dashboard/whoami",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> whoami() {
        TenantContext ctx = TenantContextHolder.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", ctx == null ? null : ctx.getTenantId());
        body.put("tenantName", ctx == null ? null : ctx.getTenantName());
        body.put("multiTenant", ctx != null && ctx.getTenantId() != null);
        return body;
    }
}
