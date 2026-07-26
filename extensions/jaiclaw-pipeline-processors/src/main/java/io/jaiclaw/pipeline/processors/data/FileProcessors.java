package io.jaiclaw.pipeline.processors.data;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.TemplateResolver;
import org.apache.camel.Exchange;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * File read + write. Paths are template-rendered so they can pull from
 * {@code {{input}}}, {@code {{stages.X.output}}}, etc.
 *
 * <p><b>Multi-tenancy.</b> In MULTI mode the processors prefix every
 * path with the resolved tenant id — matches the conformance checklist
 * in {@code CLAUDE.md}. A path config of {@code "reports/x.txt"}
 * becomes {@code "{tenantId}/reports/x.txt"} relative to the injected
 * {@code baseDir}. Absolute paths are rejected in MULTI mode (would
 * bypass the tenant partition).
 */
public final class FileProcessors {

    private FileProcessors() {}

    @PipelineProcessor(
            name = "File Read",
            category = "Data",
            description = "Read a UTF-8 file relative to the configured base directory",
            icon = "file")
    public static class Read implements ConfigurableStageProcessor {

        private final Path baseDir;
        private final TenantGuard tenantGuard;

        public Read(Path baseDir, TenantGuard tenantGuard) {
            this.baseDir = baseDir;
            this.tenantGuard = tenantGuard;
        }

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) throws Exception {
            String pathTemplate = config.get("path");
            if (pathTemplate == null || pathTemplate.isBlank()) {
                throw new IllegalArgumentException("File Read requires 'path' config");
            }
            Path target = resolvePath(pathTemplate, context, baseDir, tenantGuard);
            String content = Files.readString(target, StandardCharsets.UTF_8);
            exchange.getIn().setBody(content);
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "required": ["path"],
                      "properties": {
                        "path": { "type": "string", "description": "Relative path under the configured base dir (template-rendered)" }
                      }
                    }""";
        }
    }

    @PipelineProcessor(
            name = "File Write",
            category = "Data",
            description = "Write the current body to a UTF-8 file relative to the configured base directory",
            icon = "file")
    public static class Write implements ConfigurableStageProcessor {

        private final Path baseDir;
        private final TenantGuard tenantGuard;

        public Write(Path baseDir, TenantGuard tenantGuard) {
            this.baseDir = baseDir;
            this.tenantGuard = tenantGuard;
        }

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) throws Exception {
            String pathTemplate = config.get("path");
            if (pathTemplate == null || pathTemplate.isBlank()) {
                throw new IllegalArgumentException("File Write requires 'path' config");
            }
            Path target = resolvePath(pathTemplate, context, baseDir, tenantGuard);
            Files.createDirectories(target.getParent());
            String body = exchange.getIn().getBody(String.class);
            if (body == null) body = "";
            Files.writeString(target, body, StandardCharsets.UTF_8);
            // Pass through — body unchanged.
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "required": ["path"],
                      "properties": {
                        "path": { "type": "string", "description": "Relative path under the configured base dir (template-rendered)" }
                      }
                    }""";
        }
    }

    // ── shared ──────────────────────────────────────

    private static Path resolvePath(String template, PipelineContext context,
                                    Path baseDir, TenantGuard tenantGuard) {
        String rendered = TemplateResolver.resolve(template, context);
        if (rendered == null) rendered = "";
        rendered = rendered.trim();
        if (rendered.isEmpty()) {
            throw new IllegalArgumentException("File path resolves to empty");
        }
        Path relative = Paths.get(rendered);
        boolean multi = tenantGuard != null && tenantGuard.isMultiTenant();
        if (relative.isAbsolute() && multi) {
            throw new IllegalArgumentException(
                    "Absolute file paths are not allowed in MULTI-tenant mode: " + rendered);
        }
        if (relative.isAbsolute()) {
            return relative.normalize();
        }
        Path base = baseDir == null ? Paths.get(".") : baseDir;
        Path tenantScoped = base;
        if (multi) {
            String tenantId = tenantGuard.resolveTenantPrefix();
            if (tenantId != null && !tenantId.isBlank()) {
                tenantScoped = base.resolve(tenantId);
            }
        }
        Path resolved = tenantScoped.resolve(relative).normalize();
        // Belt-and-braces: refuse to escape the base dir via ../
        Path baseAbs = tenantScoped.toAbsolutePath().normalize();
        Path resolvedAbs = resolved.toAbsolutePath().normalize();
        if (!resolvedAbs.startsWith(baseAbs)) {
            throw new IllegalArgumentException(
                    "Resolved path '" + resolvedAbs + "' escapes base '" + baseAbs + "'");
        }
        return resolvedAbs;
    }
}
