package io.jaiclaw.config

import spock.lang.Specification

class ToolSearchPropertiesSpec extends Specification {

    def "defaults are off with no rules"() {
        when:
        def p = ToolSearchProperties.defaults()

        then:
        !p.enabled()
        !p.hasDeferralRules()
        p.limit() == 5
    }

    def "matching by exact name, section and source"() {
        given:
        def p = new ToolSearchProperties(true, ["file_read"], [], ["k8s"], ["mcp"], 5)

        expect:
        p.matches("file_read", "files", "builtin")
        p.matches("anything", "k8s", "builtin")
        p.matches("anything", "misc", "mcp")
        !p.matches("file_write", "files", "builtin")
    }

    def "glob matching over tool names"() {
        given:
        def p = new ToolSearchProperties(true, [], ["kubectl_*", "*_debug", "tool_?"], [], [], 5)

        expect:
        p.matches(name, "misc", "builtin") == expected

        where:
        name             | expected
        "kubectl_get"    | true
        "kubectl_"       | true
        "verbose_debug"  | true
        "tool_a"         | true
        "tool_ab"        | false
        "file_read"      | false
    }

    def "glob special characters in the tool name are not treated as regex"() {
        expect: "a dot is a literal dot, so 'a.b' is not matched by 'axb'"
        ToolSearchProperties.globMatches("a.b", "a.b")
        !ToolSearchProperties.globMatches("a.b", "axb")
    }

    def "null inputs never match"() {
        given:
        def p = new ToolSearchProperties(true, ["x"], ["*"], ["s"], ["src"], 5)

        expect:
        !p.matches(null, null, null)
    }

    def "a non-positive limit falls back to the default"() {
        expect:
        new ToolSearchProperties(true, [], [], [], [], value).limit() == 5

        where:
        value << [0, -1]
    }

    def "exactly one public constructor — the Boot 4 record-binder rule"() {
        expect:
        ToolSearchProperties.getConstructors().length == 1
    }
}
