package io.jaiclaw.asciirender.profile

import spock.lang.Specification

class AsciiRenderProfilesSpec extends Specification {

    def cleanup() {
        // Each spec mutates the static registry; reset before the next one runs.
        AsciiRenderProfiles.registerBuiltIns()
    }

    def "built-in profiles are present after class load"() {
        expect:
        AsciiRenderProfiles.get("shell_80") != null
        AsciiRenderProfiles.get("telegram_desktop") != null
        AsciiRenderProfiles.get("telegram_mobile") != null
        AsciiRenderProfiles.get("slack_desktop") != null
        AsciiRenderProfiles.get("discord_desktop") != null
        AsciiRenderProfiles.get("email") != null
    }

    def "telegram_mobile is narrow with no padding"() {
        when:
        def profile = AsciiRenderProfiles.get("telegram_mobile")

        then:
        profile.width() == 30
        profile.padding() == 0
    }

    def "shell_80 is the default fallback"() {
        expect:
        AsciiRenderProfiles.defaultName() == "shell_80"
        AsciiRenderProfiles.defaultProfile().name() == "shell_80"
    }

    def "getOrDefault returns the named profile when present"() {
        when:
        def profile = AsciiRenderProfiles.getOrDefault("slack_desktop")

        then:
        profile.name() == "slack_desktop"
    }

    def "getOrDefault returns the default when name is null or blank"() {
        expect:
        AsciiRenderProfiles.getOrDefault(null).name() == "shell_80"
        AsciiRenderProfiles.getOrDefault("").name() == "shell_80"
        AsciiRenderProfiles.getOrDefault("   ").name() == "shell_80"
    }

    def "getOrDefault returns the default when name is unknown (and logs a warning)"() {
        when:
        def profile = AsciiRenderProfiles.getOrDefault("nonexistent_profile")

        then:
        profile.name() == "shell_80"
    }

    def "register adds a new profile"() {
        given:
        def custom = new AsciiRenderProfile("iphone_bigtext", 24, 0)

        when:
        AsciiRenderProfiles.register(custom)

        then:
        AsciiRenderProfiles.get("iphone_bigtext") == custom
        "iphone_bigtext" in AsciiRenderProfiles.names()
    }

    def "register replaces an existing profile"() {
        given:
        def replacement = new AsciiRenderProfile("telegram_mobile", 32, 1)

        when:
        AsciiRenderProfiles.register(replacement)

        then:
        AsciiRenderProfiles.get("telegram_mobile").width() == 32
        AsciiRenderProfiles.get("telegram_mobile").padding() == 1
    }

    def "register rejects null"() {
        when:
        AsciiRenderProfiles.register(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("must not be null")
    }

    def "setDefault changes the active default"() {
        when:
        AsciiRenderProfiles.setDefault("slack_desktop")

        then:
        AsciiRenderProfiles.defaultName() == "slack_desktop"
        AsciiRenderProfiles.defaultProfile().name() == "slack_desktop"
    }

    def "setDefault rejects an unknown profile"() {
        when:
        AsciiRenderProfiles.setDefault("does_not_exist")

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("does_not_exist")
        ex.message.contains("not registered")
    }

    def "setDefault rejects null or blank"() {
        when:
        AsciiRenderProfiles.setDefault(input)

        then:
        thrown(IllegalArgumentException)

        where:
        input << [null, "", "   "]
    }

    def "AsciiRenderProfile constructor validates width range"() {
        when:
        new AsciiRenderProfile("x", width, 0)

        then:
        thrown(IllegalArgumentException)

        where:
        width << [-1, 0, 3, 501, 9999]
    }

    def "AsciiRenderProfile constructor validates padding range"() {
        when:
        new AsciiRenderProfile("x", 80, padding)

        then:
        thrown(IllegalArgumentException)

        where:
        padding << [-1, 17, 100]
    }

    def "AsciiRenderProfile constructor rejects null/blank name"() {
        when:
        new AsciiRenderProfile(name, 80, 0)

        then:
        thrown(IllegalArgumentException)

        where:
        name << [null, "", "   "]
    }
}
