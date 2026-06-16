package io.jaiclaw.autoconfigure

import io.jaiclaw.asciirender.profile.AsciiRenderProfiles
import io.jaiclaw.tools.builtin.ascii.AsciiRenderProfilesInitializer
import io.jaiclaw.tools.builtin.ascii.AsciiRenderProperties
import spock.lang.Specification

/**
 * Binding + initialization tests for {@link AsciiRenderProperties}
 * and {@link AsciiRenderProfilesInitializer}. Verifies that operator-
 * supplied profiles land in the static registry and that the
 * default-profile setting is applied.
 */
class AsciiRenderPropertiesBindingSpec extends Specification {

    def cleanup() {
        // Each spec mutates the static registry; reset before the next one runs.
        AsciiRenderProfiles.registerBuiltIns()
    }

    def "compact constructor defaults default-profile to shell_80"() {
        when:
        def props = new AsciiRenderProperties(null, null)

        then:
        props.defaultProfile() == "shell_80"
        props.profiles() == [:]
    }

    def "blank default-profile string is normalised to shell_80"() {
        when:
        def props = new AsciiRenderProperties("   ", [:])

        then:
        props.defaultProfile() == "shell_80"
    }

    def "initializer registers operator profiles into the global registry"() {
        given:
        def props = new AsciiRenderProperties(
                "shell_80",
                [
                        iphone_bigtext: new AsciiRenderProperties.ProfileConfig(24, 0),
                        custom_wide   : new AsciiRenderProperties.ProfileConfig(120, 2)
                ])

        when:
        new AsciiRenderProfilesInitializer(props)

        then:
        AsciiRenderProfiles.get("iphone_bigtext").width() == 24
        AsciiRenderProfiles.get("iphone_bigtext").padding() == 0
        AsciiRenderProfiles.get("custom_wide").width() == 120
        AsciiRenderProfiles.get("custom_wide").padding() == 2
    }

    def "initializer overrides a built-in profile when an operator entry has the same name"() {
        given:
        def props = new AsciiRenderProperties(
                "shell_80",
                [telegram_mobile: new AsciiRenderProperties.ProfileConfig(32, 1)])

        when:
        new AsciiRenderProfilesInitializer(props)

        then:
        AsciiRenderProfiles.get("telegram_mobile").width() == 32
        AsciiRenderProfiles.get("telegram_mobile").padding() == 1
    }

    def "initializer applies the configured default-profile"() {
        given:
        def props = new AsciiRenderProperties("slack_desktop", [:])

        when:
        new AsciiRenderProfilesInitializer(props)

        then:
        AsciiRenderProfiles.defaultName() == "slack_desktop"
    }

    def "default-profile can reference an operator-defined profile"() {
        given:
        def props = new AsciiRenderProperties(
                "iphone_bigtext",
                [iphone_bigtext: new AsciiRenderProperties.ProfileConfig(24, 0)])

        when: "registration runs first, then setDefault — order matters"
        new AsciiRenderProfilesInitializer(props)

        then:
        AsciiRenderProfiles.defaultName() == "iphone_bigtext"
        AsciiRenderProfiles.defaultProfile().width() == 24
    }

    def "invalid profile values are skipped with a warning"() {
        given: "padding 99 is out of range — the AsciiRenderProfile constructor will reject it"
        def props = new AsciiRenderProperties(
                "shell_80",
                [bad_profile: new AsciiRenderProperties.ProfileConfig(80, 99)])

        when:
        new AsciiRenderProfilesInitializer(props)

        then: "the bad profile did NOT land in the registry — initializer logged a warning instead"
        AsciiRenderProfiles.get("bad_profile") == null
    }

    def "unknown default-profile is logged but does not crash startup"() {
        given:
        def props = new AsciiRenderProperties("nonexistent", [:])

        when:
        new AsciiRenderProfilesInitializer(props)

        then: "registry still has its previous default"
        AsciiRenderProfiles.defaultName() == "shell_80"
    }
}
