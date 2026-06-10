package io.jaiclaw.core.tool.param

import spock.lang.Specification

/**
 * 0.8.0 P3.2: locks the {@link ParameterBinder} reflection binding.
 */
class ParameterBinderSpec extends Specification {

    static record SimpleParams(
            @ToolParameter(description = "the url") String url,
            @ToolParameter(description = "timeout", required = false) Integer timeout
    ) {}

    static record PrimitiveParams(
            @ToolParameter(description = "n", required = false) int count,
            @ToolParameter(description = "flag", required = false) boolean flag
    ) {}

    static record AllNumeric(
            @ToolParameter(description = "i") Integer i,
            @ToolParameter(description = "l") Long l,
            @ToolParameter(description = "d") Double d,
            @ToolParameter(description = "f") Float f
    ) {}

    def "binds a simple record with required + optional fields"() {
        when:
        SimpleParams p = ParameterBinder.bind([url: "https://example.com", timeout: 45], SimpleParams.class)

        then:
        p.url() == "https://example.com"
        p.timeout() == 45
    }

    def "uses null for optional fields when absent"() {
        when:
        SimpleParams p = ParameterBinder.bind([url: "https://example.com"], SimpleParams.class)

        then:
        p.url() == "https://example.com"
        p.timeout() == null
    }

    def "throws on missing required field"() {
        when:
        ParameterBinder.bind([:], SimpleParams.class)

        then:
        thrown(IllegalArgumentException)
    }

    def "primitive fields get Java defaults when absent and not required"() {
        when:
        PrimitiveParams p = ParameterBinder.bind([:], PrimitiveParams.class)

        then:
        p.count() == 0
        !p.flag()
    }

    def "coerces Number subtypes onto declared numeric type"() {
        when:
        AllNumeric p = ParameterBinder.bind(
                [i: 7L, l: 8, d: 1, f: 2.5d],
                AllNumeric.class)

        then:
        p.i() == 7
        p.l() == 8L
        p.d() == 1.0d
        p.f() == 2.5f
    }

    def "parses numeric strings (LLM-as-string fallback)"() {
        when:
        SimpleParams p = ParameterBinder.bind(
                [url: "x", timeout: "120"], SimpleParams.class)

        then:
        p.timeout() == 120
    }

    def "rejects non-record target type"() {
        when:
        ParameterBinder.bind([:], String.class)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects unparseable numeric strings"() {
        when:
        ParameterBinder.bind([url: "x", timeout: "not-a-number"], SimpleParams.class)

        then:
        thrown(IllegalArgumentException)
    }

    def "null parameters map is treated as empty"() {
        when:
        ParameterBinder.bind(null, SimpleParams.class)

        then:
        thrown(IllegalArgumentException)   // url is required
    }
}
