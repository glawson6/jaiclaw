package io.jaiclaw.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Federal compliance (2026-08): generate a compliance-posture report
 * combining what {@code docs/compliance/*.md} claims with a spot-check
 * against the actual code state.
 *
 * <p>Bound to the {@code verify} phase by default so it runs as part of
 * {@code mvn verify} without extra configuration. Every finding is a
 * WARN (not failure) in v1 — the report is informational, not a gate.
 * Adopters who want gating can wire the goal to {@code validate} and
 * pass {@code -Djaiclaw.compliance-report.failOnGap=true}.
 *
 * <p>The full audit skill (backlog: {@code docs/issues/compliance-scan-skill.md})
 * is the interactive counterpart to this Mojo — same evidence, different
 * invocation model.
 *
 * <p>Report output: {@code target/jaiclaw-compliance-report.md}. Skipped
 * (with an INFO log) if {@code docs/compliance/} is not present.
 */
@Mojo(name = "compliance-report", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ComplianceReportMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File baseDir;

    /**
     * Root directory of the compliance-docs tree. Defaults to
     * {@code docs/compliance/} under the project base directory.
     */
    @Parameter(property = "jaiclaw.compliance-report.docsDir",
            defaultValue = "${project.basedir}/docs/compliance")
    private File docsDir;

    /**
     * Output file for the generated report. Defaults to
     * {@code target/jaiclaw-compliance-report.md}.
     */
    @Parameter(property = "jaiclaw.compliance-report.output",
            defaultValue = "${project.build.directory}/jaiclaw-compliance-report.md")
    private File outputFile;

    /**
     * When true, the goal fails the build if any REGRESSION is detected
     * (a documented capability no longer present in code). Default false —
     * the report is informational only.
     */
    @Parameter(property = "jaiclaw.compliance-report.failOnGap",
            defaultValue = "false")
    private boolean failOnGap;

    // The 8 deep-dive slugs we expect to find in docs/compliance/.
    private static final List<String> EXPECTED_REGULATIONS = List.of(
            "section-508", "fedramp", "fisma", "nist-800-53",
            "fips-140-3", "cmmc", "hipaa", "gdpr"
    );

    // Grep targets: file:pattern pairs the check verifies exist.
    // Absent-in-code but claimed-in-docs = REGRESSION.
    private static final Map<String, List<CodeClaim>> CLAIMS_BY_REGULATION = new LinkedHashMap<>();
    static {
        CLAIMS_BY_REGULATION.put("section-508", List.of(
                new CodeClaim("aria-label", "extensions/jaiclaw-pipeline-dashboard/src/main/resources/jaiclaw-pipeline-dashboard/dashboard.html"),
                new CodeClaim("altText", "core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/AsciiRenderTool.java"),
                new CodeClaim("jsx-a11y", "apps/jaiclaw-pipeline-studio/frontend/package.json")
        ));
        CLAIMS_BY_REGULATION.put("fedramp", List.of(
                new CodeClaim("FedRampWarningChatModelDecorator", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/fedramp/FedRampWarningChatModelDecorator.java"),
                new CodeClaim("KEY_FEDRAMP_IMPACT", "core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java"),
                new CodeClaim("FEDRAMP_MODERATE", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProfile.java")
        ));
        CLAIMS_BY_REGULATION.put("fisma", List.of(
                new CodeClaim("HashChainedAuditLogger", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/HashChainedAuditLogger.java")
        ));
        CLAIMS_BY_REGULATION.put("nist-800-53", List.of(
                new CodeClaim("RequireHttpsStartupGuard", "core/jaiclaw-security/src/main/java/io/jaiclaw/security/RequireHttpsStartupGuard.java"),
                new CodeClaim("AesGcmFieldEncryptor", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/AesGcmFieldEncryptor.java"),
                new CodeClaim("TenantGuard", "core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantGuard.java")
        ));
        CLAIMS_BY_REGULATION.put("fips-140-3", List.of(
                new CodeClaim("FipsPostureStartupCheck", "core/jaiclaw-security/src/main/java/io/jaiclaw/security/FipsPostureStartupCheck.java"),
                new CodeClaim("AES/GCM/NoPadding", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/AesGcmFieldEncryptor.java")
        ));
        CLAIMS_BY_REGULATION.put("cmmc", List.of(
                new CodeClaim("CuiWarningChatModelDecorator", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/cui/CuiWarningChatModelDecorator.java"),
                new CodeClaim("KEY_CUI_PROCESSING", "core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java"),
                new CodeClaim("CMMC_L2", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProfile.java")
        ));
        CLAIMS_BY_REGULATION.put("hipaa", List.of(
                new CodeClaim("BaaWarningChatModelDecorator", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/BaaWarningChatModelDecorator.java"),
                new CodeClaim("RegexPromptRedactor", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/gdpr/RegexPromptRedactor.java"),
                new CodeClaim("KEY_PHI_PROCESSING", "core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java")
        ));
        CLAIMS_BY_REGULATION.put("gdpr", List.of(
                new CodeClaim("AggregateDataSubjectErasureSpi", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/gdpr/AggregateDataSubjectErasureSpi.java"),
                new CodeClaim("GdprController", "extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/gdpr/GdprController.java"),
                new CodeClaim("KEY_LAWFUL_BASIS", "core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java")
        ));
    }

    @Override
    public void execute() throws MojoExecutionException {
        Path docs = docsDir.toPath();
        if (!Files.isDirectory(docs)) {
            getLog().info("Compliance docs directory missing at " + docs + " — skipping compliance report.");
            return;
        }

        List<Finding> findings = new ArrayList<>();

        // 1. Verify all 8 expected deep-dives exist.
        for (String slug : EXPECTED_REGULATIONS) {
            Path deepDive = docs.resolve(slug + ".md");
            if (!Files.isRegularFile(deepDive)) {
                findings.add(new Finding(slug, "MISSING_DOC",
                        "Deep-dive file missing at docs/compliance/" + slug + ".md"));
            }
        }

        // 2. Verify each documented code claim actually exists in source.
        for (Map.Entry<String, List<CodeClaim>> entry : CLAIMS_BY_REGULATION.entrySet()) {
            String slug = entry.getKey();
            for (CodeClaim claim : entry.getValue()) {
                Path target = baseDir.toPath().resolve(claim.filePath);
                if (!Files.isRegularFile(target)) {
                    findings.add(new Finding(slug, "REGRESSION",
                            "Documented code file missing: " + claim.filePath));
                    continue;
                }
                try {
                    String content = Files.readString(target, StandardCharsets.UTF_8);
                    if (!content.contains(claim.searchToken)) {
                        findings.add(new Finding(slug, "REGRESSION",
                                "Documented symbol '" + claim.searchToken
                                        + "' not found in " + claim.filePath));
                    }
                } catch (IOException e) {
                    findings.add(new Finding(slug, "IO_ERROR",
                            "Failed to read " + claim.filePath + ": " + e.getMessage()));
                }
            }
        }

        // 3. Write the report.
        try {
            Files.createDirectories(outputFile.toPath().getParent());
            Files.writeString(outputFile.toPath(), renderReport(findings), StandardCharsets.UTF_8);
            getLog().info("Compliance report written to " + outputFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write compliance report: " + e.getMessage(), e);
        }

        // 4. Optionally fail the build.
        long regressions = findings.stream().filter(f -> "REGRESSION".equals(f.severity)).count();
        if (regressions > 0) {
            String msg = regressions + " compliance regression(s) detected — see " + outputFile;
            if (failOnGap) {
                throw new MojoExecutionException(msg);
            } else {
                getLog().warn(msg);
            }
        }
    }

    private String renderReport(List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("# JaiClaw Compliance Report\n\n");
        sb.append("**Generated:** ").append(LocalDate.now()).append("\n");
        sb.append("**Source of truth:** `docs/compliance/*.md`\n");
        sb.append("**Findings:** ").append(findings.size()).append("\n\n");

        if (findings.isEmpty()) {
            sb.append("## OK — all 8 regulations have documented deep-dives and their claimed code paths exist.\n\n");
            sb.append("Regulations verified:\n");
            for (String slug : EXPECTED_REGULATIONS) {
                sb.append("- `").append(slug).append("`\n");
            }
            return sb.toString();
        }

        sb.append("## Findings\n\n");
        sb.append("| Regulation | Severity | Detail |\n");
        sb.append("|---|---|---|\n");
        for (Finding f : findings) {
            sb.append("| ").append(f.regulation)
                    .append(" | ").append(f.severity)
                    .append(" | ").append(f.detail).append(" |\n");
        }
        sb.append("\n## Notes\n\n");
        sb.append("- `REGRESSION` — a claim in the docs no longer matches code. Fix by updating either the docs or restoring the code.\n");
        sb.append("- `MISSING_DOC` — a per-regulation deep-dive file is absent. All 8 are expected under `docs/compliance/`.\n");
        sb.append("- `IO_ERROR` — the goal couldn't read a source file. Investigate manually.\n");
        sb.append("\nFor an interactive audit with more detailed phase-by-phase coverage, see the backlog for the `/compliance-scan` skill: `docs/issues/compliance-scan-skill.md`.\n");
        return sb.toString();
    }

    /** A single "documented capability" claim: a source file that must contain a specific token. */
    private record CodeClaim(String searchToken, String filePath) {}

    /** A finding — one line in the report table. */
    private record Finding(String regulation, String severity, String detail) {}
}
