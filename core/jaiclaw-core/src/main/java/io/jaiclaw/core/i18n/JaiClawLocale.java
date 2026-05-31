package io.jaiclaw.core.i18n;

import java.util.Locale;

/**
 * Supported locales for JaiClaw's user-facing messages.
 */
public enum JaiClawLocale {

    ENGLISH(Locale.ENGLISH),
    CHINESE_SIMPLIFIED(Locale.SIMPLIFIED_CHINESE),
    SPANISH(Locale.of("es")),
    PORTUGUESE_BRAZIL(Locale.of("pt", "BR")),
    GERMAN(Locale.GERMAN),
    FRENCH(Locale.FRENCH),
    JAPANESE(Locale.JAPANESE),
    KOREAN(Locale.KOREAN),
    ARABIC(Locale.of("ar")),
    TURKISH(Locale.of("tr"));

    private final Locale locale;

    JaiClawLocale(Locale locale) {
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }

    /**
     * Resolve a JaiClawLocale from a language tag (e.g. "en", "zh-CN", "pt-BR").
     * Returns {@link #ENGLISH} if no match is found.
     */
    public static JaiClawLocale fromTag(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return ENGLISH;
        }
        Locale parsed = Locale.forLanguageTag(languageTag.replace("_", "-"));
        for (JaiClawLocale jcl : values()) {
            if (jcl.locale.getLanguage().equals(parsed.getLanguage())) {
                // For locales with country variants (zh-CN, pt-BR), also match country
                if (!jcl.locale.getCountry().isEmpty()) {
                    if (jcl.locale.getCountry().equals(parsed.getCountry())) {
                        return jcl;
                    }
                } else {
                    return jcl;
                }
            }
        }
        return ENGLISH;
    }
}
