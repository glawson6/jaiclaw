package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.core.gdpr.PrivacyNoticeService;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T2-7 reference {@link PrivacyNoticeService} — an in-memory record of which
 * subjects have seen the notice. First-time delivery emits a
 * {@code privacy.notice_displayed} audit event; explicit acceptance emits a
 * {@code privacy.notice_accepted} event.
 *
 * <p>Notice text is a single string configured on construction. Adopters
 * needing locale-specific text should subclass and override
 * {@link #noticeTextFor(String)}.
 */
public class DefaultPrivacyNoticeService implements PrivacyNoticeService {

    public static final String ACTION_DISPLAYED = "privacy.notice_displayed";
    public static final String ACTION_ACCEPTED = "privacy.notice_accepted";

    /** tenantId:dataSubjectId → "seen" / "accepted". */
    private final Map<String, String> state = new ConcurrentHashMap<>();
    private final String defaultNoticeText;
    private final List<AuditLogger> auditLoggers;
    private final Clock clock;

    public DefaultPrivacyNoticeService(String defaultNoticeText, List<AuditLogger> auditLoggers) {
        this(defaultNoticeText, auditLoggers, Clock.systemUTC());
    }

    public DefaultPrivacyNoticeService(String defaultNoticeText, List<AuditLogger> auditLoggers, Clock clock) {
        this.defaultNoticeText = defaultNoticeText == null ? "" : defaultNoticeText;
        this.auditLoggers = List.copyOf(auditLoggers == null ? List.of() : auditLoggers);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public String ensureNoticeDelivered(String tenantId, String dataSubjectId, String locale) {
        validate(tenantId, dataSubjectId);
        String key = key(tenantId, dataSubjectId);
        if (state.containsKey(key)) return null;
        state.put(key, "seen");
        String text = noticeTextFor(locale);
        emit(ACTION_DISPLAYED, tenantId, dataSubjectId, Map.of("locale", locale == null ? "" : locale));
        return text;
    }

    @Override
    public void recordAcceptance(String tenantId, String dataSubjectId) {
        validate(tenantId, dataSubjectId);
        state.put(key(tenantId, dataSubjectId), "accepted");
        emit(ACTION_ACCEPTED, tenantId, dataSubjectId, Map.of());
    }

    @Override
    public boolean hasSeenNotice(String tenantId, String dataSubjectId) {
        if (tenantId == null || dataSubjectId == null) return false;
        return state.containsKey(key(tenantId, dataSubjectId));
    }

    /** Override to return locale-specific text. Default ignores {@code locale}. */
    protected String noticeTextFor(String locale) {
        return defaultNoticeText;
    }

    private void emit(String action, String tenantId, String dataSubjectId, Map<String, Object> extra) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(clock.instant())
                .tenantId(tenantId)
                .actor("system")
                .action(action)
                .resource("subject:" + dataSubjectId)
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(extra)
                .dataCategories(Set.of("privacy_notice"))
                .build();
        for (AuditLogger logger : auditLoggers) {
            try {
                logger.log(event);
            } catch (RuntimeException ignored) {
                // Never fail the message pipeline because an audit sink threw.
            }
        }
    }

    private static void validate(String tenantId, String dataSubjectId) {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId must not be blank");
        if (dataSubjectId == null || dataSubjectId.isBlank())
            throw new IllegalArgumentException("dataSubjectId must not be blank");
    }

    private static String key(String tenantId, String dataSubjectId) {
        return tenantId + ":" + dataSubjectId;
    }
}
