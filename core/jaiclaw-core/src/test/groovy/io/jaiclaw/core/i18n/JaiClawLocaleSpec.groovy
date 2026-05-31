package io.jaiclaw.core.i18n

import spock.lang.Specification
import spock.lang.Unroll

class JaiClawLocaleSpec extends Specification {

    def "all locales have non-null java.util.Locale"() {
        expect:
        JaiClawLocale.values().every { it.locale() != null }
    }

    def "ENGLISH maps to Locale.ENGLISH"() {
        expect:
        JaiClawLocale.ENGLISH.locale() == Locale.ENGLISH
    }

    def "CHINESE_SIMPLIFIED maps to zh-CN"() {
        expect:
        JaiClawLocale.CHINESE_SIMPLIFIED.locale().language == "zh"
        JaiClawLocale.CHINESE_SIMPLIFIED.locale().country == "CN"
    }

    def "PORTUGUESE_BRAZIL maps to pt-BR"() {
        expect:
        JaiClawLocale.PORTUGUESE_BRAZIL.locale().language == "pt"
        JaiClawLocale.PORTUGUESE_BRAZIL.locale().country == "BR"
    }

    @Unroll
    def "fromTag('#tag') returns #expected"() {
        expect:
        JaiClawLocale.fromTag(tag) == expected

        where:
        tag      | expected
        "en"     | JaiClawLocale.ENGLISH
        "es"     | JaiClawLocale.SPANISH
        "de"     | JaiClawLocale.GERMAN
        "fr"     | JaiClawLocale.FRENCH
        "ja"     | JaiClawLocale.JAPANESE
        "ko"     | JaiClawLocale.KOREAN
        "ar"     | JaiClawLocale.ARABIC
        "tr"     | JaiClawLocale.TURKISH
        "zh-CN"  | JaiClawLocale.CHINESE_SIMPLIFIED
        "pt-BR"  | JaiClawLocale.PORTUGUESE_BRAZIL
    }

    def "fromTag with underscore separator works"() {
        expect:
        JaiClawLocale.fromTag("zh_CN") == JaiClawLocale.CHINESE_SIMPLIFIED
        JaiClawLocale.fromTag("pt_BR") == JaiClawLocale.PORTUGUESE_BRAZIL
    }

    def "fromTag returns ENGLISH for null"() {
        expect:
        JaiClawLocale.fromTag(null) == JaiClawLocale.ENGLISH
    }

    def "fromTag returns ENGLISH for blank"() {
        expect:
        JaiClawLocale.fromTag("") == JaiClawLocale.ENGLISH
        JaiClawLocale.fromTag("  ") == JaiClawLocale.ENGLISH
    }

    def "fromTag returns ENGLISH for unknown language"() {
        expect:
        JaiClawLocale.fromTag("xx") == JaiClawLocale.ENGLISH
    }

    def "enum has exactly 10 locales"() {
        expect:
        JaiClawLocale.values().length == 10
    }
}
