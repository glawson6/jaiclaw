package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.core.gdpr.DataSubjectErasureSpi;
import io.jaiclaw.core.gdpr.DataSubjectExportService;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T2-2 — REST surface for GDPR Art. 15 / Art. 20 (export) and Art. 17
 * (erasure) requests.
 *
 * <p>Tenant scoping is resolved from {@link TenantContextHolder} — the same
 * mechanism the rest of the framework uses. The upstream auth layer is
 * responsible for populating the tenant context; if it's absent the
 * controller returns 403 rather than falling through to a global scope.
 *
 * <p>Rate-limiting + auth belong at the gateway layer and aren't wired here.
 * Adopters SHOULD front this controller with rate-limiter middleware and
 * require a role like {@code gdpr.operator} on the calling principal.
 */
@RestController
@RequestMapping("/api/gdpr")
public class GdprController {

    private static final Logger log = LoggerFactory.getLogger(GdprController.class);

    private final DataSubjectExportService exportService;
    private final DataSubjectErasureSpi erasureSpi;

    public GdprController(DataSubjectExportService exportService, DataSubjectErasureSpi erasureSpi) {
        this.exportService = exportService;
        this.erasureSpi = erasureSpi;
    }

    /**
     * GDPR Art. 15 / Art. 20 — export every stored artifact for a data subject.
     *
     * @param dataSubjectId the subject identifier
     * @param format        {@code json} (default), {@code json_ld}, {@code csv_bundle}
     */
    @GetMapping("/export/{dataSubjectId}")
    public ResponseEntity<DataSubjectExportService.DataSubjectExport> export(
            @PathVariable String dataSubjectId,
            @RequestParam(defaultValue = "json") String format) {
        String tenantId = resolveTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Rejected GDPR export — no tenant context");
            return ResponseEntity.status(403).build();
        }
        DataSubjectExportService.ExportFormat fmt = parseFormat(format);
        DataSubjectExportService.DataSubjectExport export =
                exportService.exportForDataSubject(tenantId, dataSubjectId, fmt);
        return ResponseEntity.ok(export);
    }

    /**
     * GDPR Art. 17 — erase every stored artifact for a data subject.
     */
    @DeleteMapping("/subject/{dataSubjectId}")
    public ResponseEntity<DataSubjectErasureSpi.ErasureResult> erase(
            @PathVariable String dataSubjectId,
            @RequestParam(defaultValue = "ART_17_REQUEST") String reason) {
        String tenantId = resolveTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Rejected GDPR erasure — no tenant context");
            return ResponseEntity.status(403).build();
        }
        DataSubjectErasureSpi.ErasureReason parsed;
        try {
            parsed = DataSubjectErasureSpi.ErasureReason.valueOf(reason);
        } catch (IllegalArgumentException ex) {
            parsed = DataSubjectErasureSpi.ErasureReason.OPERATOR_INITIATED;
        }
        DataSubjectErasureSpi.ErasureResult result =
                erasureSpi.eraseForDataSubject(tenantId, dataSubjectId, parsed);
        return ResponseEntity.ok(result);
    }

    private String resolveTenantId() {
        io.jaiclaw.core.tenant.TenantContext ctx = TenantContextHolder.get();
        return ctx == null ? null : ctx.getTenantId();
    }

    private DataSubjectExportService.ExportFormat parseFormat(String s) {
        if (s == null) return DataSubjectExportService.ExportFormat.JSON;
        return switch (s.toLowerCase()) {
            case "json_ld", "jsonld" -> DataSubjectExportService.ExportFormat.JSON_LD;
            case "csv_bundle", "csv" -> DataSubjectExportService.ExportFormat.CSV_BUNDLE;
            default -> DataSubjectExportService.ExportFormat.JSON;
        };
    }
}
