package io.jaiclaw.core.i18n

import spock.lang.Specification

class JaiClawMessagesSpec extends Specification {

    def "getDefault returns English instance"() {
        when:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        then:
        messages.locale() == Locale.ENGLISH
    }

    def "forLocale with null returns default"() {
        expect:
        JaiClawMessages.forLocale((Locale) null).locale() == Locale.ENGLISH
    }

    def "forLocale with JaiClawLocale null returns default"() {
        expect:
        JaiClawMessages.forLocale((JaiClawLocale) null).locale() == Locale.ENGLISH
    }

    def "forLocale with ENGLISH returns default singleton"() {
        expect:
        JaiClawMessages.forLocale(Locale.ENGLISH).is(JaiClawMessages.getDefault())
    }

    def "get returns English message for known key"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.get("error.authentication.required") == "Authentication required"
    }

    def "get returns key itself for unknown key"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.get("nonexistent.key") == "nonexistent.key"
    }

    def "get with args formats message"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.get("error.session.not.found", "sess-123") == "Session not found: sess-123"
    }

    def "get with args returns key for unknown key"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.get("nonexistent.key", "arg1") == "nonexistent.key"
    }

    // Typed accessor tests

    def "errorSessionNotFound formats session ID"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.errorSessionNotFound("sess-42") == "Session not found: sess-42"
    }

    def "errorToolNotFound formats tool name"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.errorToolNotFound("web_search") == "Tool not found: web_search"
    }

    def "statusAgentReady returns status message"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.statusAgentReady() == "Agent ready"
    }

    def "statusChannelConnected formats channel name"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.statusChannelConnected("telegram") == "Connected to telegram"
    }

    def "toolWebSearchDescription returns description"() {
        given:
        JaiClawMessages messages = JaiClawMessages.getDefault()

        expect:
        messages.toolWebSearchDescription() == "Search the web for information"
    }

    // Locale-specific tests

    def "German locale returns German messages"() {
        given:
        JaiClawMessages messages = JaiClawMessages.forLocale(JaiClawLocale.GERMAN)

        expect:
        messages.get("error.authentication.required") == "Authentifizierung erforderlich"
        messages.statusAgentReady() == "Agent bereit"
    }

    def "Spanish locale returns Spanish messages"() {
        given:
        JaiClawMessages messages = JaiClawMessages.forLocale(JaiClawLocale.SPANISH)

        expect:
        messages.get("error.access.denied") == "Acceso denegado"
    }

    def "Japanese locale returns Japanese messages"() {
        given:
        JaiClawMessages messages = JaiClawMessages.forLocale(JaiClawLocale.JAPANESE)

        expect:
        messages.get("error.authentication.required") == "\u8a8d\u8a3c\u304c\u5fc5\u8981\u3067\u3059"
    }

    def "Chinese locale formats parameterized messages"() {
        given:
        JaiClawMessages messages = JaiClawMessages.forLocale(JaiClawLocale.CHINESE_SIMPLIFIED)

        expect:
        messages.errorToolNotFound("web_search").contains("web_search")
    }

    def "all supported locales load without error"() {
        expect:
        JaiClawLocale.values().every {
            JaiClawMessages messages = JaiClawMessages.forLocale(it)
            messages.locale() != null
            messages.get("error.authentication.required") != "error.authentication.required"
        }
    }
}
