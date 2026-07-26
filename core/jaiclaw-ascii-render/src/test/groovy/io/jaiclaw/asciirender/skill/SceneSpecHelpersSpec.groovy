package io.jaiclaw.asciirender.skill

import io.jaiclaw.asciirender.profile.AsciiRenderProfile
import io.jaiclaw.asciirender.profile.AsciiRenderProfiles
import spock.lang.Specification

class SceneSpecHelpersSpec extends Specification {

    def cleanup() {
        // Reset the static default profile between tests — the module's
        // registry is process-global.
        AsciiRenderProfiles.setDefault("shell_80")
    }

    def "returns profile width when it falls inside [min, max]"() {
        given:
        AsciiRenderProfiles.setDefault("shell_80")
        int shellWidth = AsciiRenderProfiles.getOrDefault("shell_80").width()

        expect: "the profile's actual width is returned when in range"
        SceneSpecHelpers.activeWidth(40, 100) == shellWidth
    }

    def "clamps up when profile width is below min"() {
        given: "a narrow profile registered + activated"
        AsciiRenderProfiles.register(new AsciiRenderProfile("test_narrow", 30, 1))
        AsciiRenderProfiles.setDefault("test_narrow")

        expect: "min=40 wins over the profile's 30"
        SceneSpecHelpers.activeWidth(40, 100) == 40
    }

    def "clamps down when profile width is above max"() {
        given: "a wide profile registered + activated"
        AsciiRenderProfiles.register(new AsciiRenderProfile("test_wide", 200, 1))
        AsciiRenderProfiles.setDefault("test_wide")

        expect: "max=100 wins over the profile's 200"
        SceneSpecHelpers.activeWidth(40, 100) == 100
    }

    def "rejects invalid inputs"() {
        when: "min > max"
        SceneSpecHelpers.activeWidth(100, 40)

        then:
        thrown(IllegalArgumentException)

        when: "min <= 0"
        SceneSpecHelpers.activeWidth(0, 100)

        then:
        thrown(IllegalArgumentException)
    }
}
