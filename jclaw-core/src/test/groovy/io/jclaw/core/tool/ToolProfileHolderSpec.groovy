package io.jclaw.core.tool

import spock.lang.Specification

class ToolProfileHolderSpec extends Specification {

    def cleanup() {
        ToolProfileHolder.clear()
    }

    def "getOrDefault returns FULL when not set"() {
        expect:
        ToolProfileHolder.getOrDefault() == ToolProfile.FULL
    }

    def "get returns null when not set"() {
        expect:
        ToolProfileHolder.get() == null
    }

    def "stores and retrieves profile"() {
        when:
        ToolProfileHolder.set(ToolProfile.CODING)

        then:
        ToolProfileHolder.get() == ToolProfile.CODING
        ToolProfileHolder.getOrDefault() == ToolProfile.CODING
    }

    def "clear removes profile"() {
        given:
        ToolProfileHolder.set(ToolProfile.MESSAGING)

        when:
        ToolProfileHolder.clear()

        then:
        ToolProfileHolder.get() == null
        ToolProfileHolder.getOrDefault() == ToolProfile.FULL
    }

    def "each profile value can be stored and retrieved"() {
        when:
        ToolProfileHolder.set(profile)

        then:
        ToolProfileHolder.get() == profile

        cleanup:
        ToolProfileHolder.clear()

        where:
        profile << ToolProfile.values()
    }
}
