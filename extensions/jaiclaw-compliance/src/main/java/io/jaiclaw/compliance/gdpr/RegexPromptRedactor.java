package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.core.gdpr.PromptRedactor;
import io.jaiclaw.core.tenant.TenantContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T2-3 reference impl — regex-based redaction covering the six common PHI/PII
 * classes: SSN, MRN (medical record number), phone, email, DOB, credit card.
 *
 * <p>Runs only when {@link TenantContext#isPhiProcessing()} is true or the
 * constructor is told to always run ({@code strict=true}).
 *
 * <p>Matches are returned in order of their start offset for deterministic
 * audit records; the replacement text is uniform per class so operators can
 * see what was redacted without exposing content.
 */
public class RegexPromptRedactor implements PromptRedactor {

    /** Pattern class → compiled regex. Uses LinkedHashMap for deterministic order. */
    private final Map<String, Pattern> patterns;
    /** When true, redaction runs regardless of {@code TenantContext.isPhiProcessing()}. */
    private final boolean strict;

    public RegexPromptRedactor() {
        this(defaultPatterns(), false);
    }

    public RegexPromptRedactor(boolean strict) {
        this(defaultPatterns(), strict);
    }

    public RegexPromptRedactor(Map<String, Pattern> patterns, boolean strict) {
        this.patterns = new LinkedHashMap<>(patterns);
        this.strict = strict;
    }

    /**
     * The default catalog. Each pattern is documented on its own so callers
     * subclassing to add or replace an entry can see the tradeoff.
     */
    public static Map<String, Pattern> defaultPatterns() {
        Map<String, Pattern> m = new LinkedHashMap<>();
        // US SSN: NNN-NN-NNNN (loose — no exclusions for 000/666/9xx blocks)
        m.put("ssn", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"));
        // MRN: 7-10 digits prefixed by MRN or MR#. Deliberately narrow so we
        // don't flag arbitrary numbers.
        m.put("mrn", Pattern.compile("\\bMRN?[:#]?\\s*\\d{7,10}\\b", Pattern.CASE_INSENSITIVE));
        // US-shaped phone. Doesn't try to catch international.
        m.put("phone", Pattern.compile("\\b(?:\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"));
        // Email — RFC 5322 is impractical here; the common form covers real usage.
        m.put("email", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"));
        // DOB: MM/DD/YYYY or YYYY-MM-DD
        m.put("dob", Pattern.compile("\\b(?:(?:0?[1-9]|1[0-2])/(?:0?[1-9]|[12]\\d|3[01])/(?:19|20)\\d{2}"
                + "|(?:19|20)\\d{2}-(?:0?[1-9]|1[0-2])-(?:0?[1-9]|[12]\\d|3[01]))\\b"));
        // Credit card — matches Visa/MC/Amex/Discover-shaped 13-16 digit groups.
        m.put("credit_card", Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b"));
        return m;
    }

    @Override
    public RedactionResult redact(String content, TenantContext tenantContext) {
        if (content == null || content.isEmpty()) return new RedactionResult("", List.of());
        boolean phiOn = strict || (tenantContext != null && tenantContext.isPhiProcessing());
        if (!phiOn) return new RedactionResult(content, List.of());

        List<RedactionMatch> matches = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : patterns.entrySet()) {
            String label = e.getKey();
            Matcher m = e.getValue().matcher(content);
            while (m.find()) {
                matches.add(new RedactionMatch(label, m.start(), m.end(), "[REDACTED-" + label.toUpperCase() + "]"));
            }
        }
        if (matches.isEmpty()) return new RedactionResult(content, List.of());

        // Apply matches highest-start-first so offsets stay stable during replacement.
        List<RedactionMatch> byStart = new ArrayList<>(matches);
        byStart.sort((a, b) -> Integer.compare(b.start(), a.start()));
        StringBuilder buf = new StringBuilder(content);
        for (RedactionMatch match : byStart) {
            buf.replace(match.start(), match.end(), match.replacement());
        }

        matches.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return new RedactionResult(buf.toString(), matches);
    }
}
