package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;
import io.jaiclaw.core.tenant.TenantContext;

import java.util.List;

/**
 * T2-3 — SPI that redacts PHI / PII from a prompt before it's dispatched to
 * an LLM. Runs when the caller's {@link TenantContext#isPhiProcessing()} is
 * true, or when the compliance mode is {@code STRICT}.
 *
 * <p>Redaction is best-effort — regex-based impls will miss free-form
 * expressions. The SPI is a <em>risk reduction</em>, not a HIPAA safeguard
 * on its own; adopters deploying to a covered entity should still restrict
 * the LLM provider via a BAA (see {@code jaiclaw.models.providers.*.baa-eligible}).
 */
@Stable
public interface PromptRedactor {

    /**
     * Redact {@code content} in the context of {@code tenantContext}.
     *
     * @param content       the raw prompt text
     * @param tenantContext caller context — may drive category-specific
     *                      behavior (e.g. skip redaction when PHI flag off)
     * @return the redacted content + a list of matches for the audit trail
     */
    RedactionResult redact(String content, TenantContext tenantContext);

    /**
     * Outcome of a redaction pass.
     *
     * @param redactedContent the content with all matches replaced
     * @param matches         one entry per match (never null; may be empty)
     */
    record RedactionResult(String redactedContent, List<RedactionMatch> matches) {
        public RedactionResult {
            if (redactedContent == null) redactedContent = "";
            matches = matches == null ? List.of() : List.copyOf(matches);
        }

        public boolean hasMatches() { return !matches.isEmpty(); }
    }

    /**
     * A single redaction hit.
     *
     * @param pattern     the pattern label ({@code "ssn"}, {@code "mrn"}, etc.)
     * @param start       start offset in the raw content
     * @param end         end offset (exclusive) in the raw content
     * @param replacement the token that replaced the match (e.g. {@code "[REDACTED-SSN]"})
     */
    record RedactionMatch(String pattern, int start, int end, String replacement) {}
}
