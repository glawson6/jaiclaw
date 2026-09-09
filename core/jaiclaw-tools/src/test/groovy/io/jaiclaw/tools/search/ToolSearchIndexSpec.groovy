package io.jaiclaw.tools.search

import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolProfile
import spock.lang.Specification

class ToolSearchIndexSpec extends Specification {

    private static ToolDefinition def_(String name, String desc, String section = "misc",
                                       Set<String> keywords = [] as Set) {
        ToolDefinition.builder()
                .name(name).description(desc).section(section)
                .inputSchema('{"type":"object"}')
                .profiles([ToolProfile.FULL] as Set)
                .keywords(keywords)
                .build()
    }

    def "an exact name match outranks everything else"() {
        given:
        def index = ToolSearchIndex.of([
                def_("file_read", "Read the contents of a file"),
                def_("web_fetch", "Fetch a URL. Can read a file over http"),
        ])

        expect:
        index.search("file_read", 5).first().name() == "file_read"
    }

    def "natural-language queries find the right tool"() {
        given:
        def index = ToolSearchIndex.of([
                def_("file_read", "Read the contents of a file", "files"),
                def_("shell_exec", "Execute a shell command", "exec"),
                def_("kubectl_get", "List Kubernetes resources", "k8s", ["pods", "kubernetes"] as Set),
        ])

        expect:
        index.search(query, 3).first().name() == expected

        where:
        query                     | expected
        "read a file"             | "file_read"
        "run a shell command"     | "shell_exec"
        "list kubernetes pods"    | "kubectl_get"
    }

    def "keywords surface tools whose names do not contain the query terms"() {
        given: "a tool whose name says nothing about what a user would ask for"
        def index = ToolSearchIndex.of([
                def_("kubectl_get", "List resources", "k8s", ["pods", "deployments"] as Set),
                def_("file_read", "Read a file", "files"),
        ])

        expect:
        index.search("pods", 3).first().name() == "kubectl_get"
    }

    def "section names are searchable"() {
        given:
        def index = ToolSearchIndex.of([
                def_("alpha", "does a thing", "kubernetes"),
                def_("beta", "does another thing", "files"),
        ])

        expect:
        index.search("kubernetes", 3).first().name() == "alpha"
    }

    def "the limit is respected"() {
        given:
        def index = ToolSearchIndex.of((1..10).collect { def_("file_tool_$it", "read a file") })

        expect:
        index.search("file", 3).size() == 3
    }

    def "a non-matching query returns nothing rather than noise"() {
        given:
        def index = ToolSearchIndex.of([def_("file_read", "Read a file", "files")])

        expect:
        index.search("quantum entanglement telemetry", 5).isEmpty()
    }

    def "stopword-only queries return nothing"() {
        given:
        def index = ToolSearchIndex.of([def_("file_read", "Read a file")])

        expect:
        index.search("i want to the of", 5).isEmpty()
    }

    def "blank and non-positive inputs are handled"() {
        given:
        def index = ToolSearchIndex.of([def_("file_read", "Read a file")])

        expect:
        index.search(query, limit).isEmpty()

        where:
        query  | limit
        null   | 5
        ""     | 5
        "   "  | 5
        "file" | 0
        "file" | -1
    }

    def "results are stably ordered for identical queries"() {
        given: "several tools that score identically"
        def index = ToolSearchIndex.of([
                def_("zeta_file", "read a file"),
                def_("alpha_file", "read a file"),
                def_("mid_file", "read a file"),
        ])

        when:
        def first = index.search("read a file", 3)*.name()
        def second = index.search("read a file", 3)*.name()

        then: "a model seeing a different order each turn could not build a habit"
        first == second
        first == ["alpha_file", "mid_file", "zeta_file"]
    }

    def "an empty index searches safely"() {
        expect:
        ToolSearchIndex.empty().search("anything", 5).isEmpty()
        ToolSearchIndex.empty().size() == 0
    }
}
