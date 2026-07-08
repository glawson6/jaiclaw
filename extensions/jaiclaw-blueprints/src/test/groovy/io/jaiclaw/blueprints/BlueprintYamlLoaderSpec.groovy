package io.jaiclaw.blueprints

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class BlueprintYamlLoaderSpec extends Specification {

    @TempDir
    Path dir

    def "fromMap loads a fully-specified blueprint"() {
        given:
        Map<String, Object> m = [
                id: "b1",
                title: "Blueprint One",
                description: "does things",
                category: "devops",
                tags: ["security", "hourly"],
                scheduleTemplate: "0 * * * *",
                scheduleHuman: "Hourly",
                promptTemplate: "Do {x}",
                slots: [[
                        key: "x",
                        label: "X",
                        type: "TEXT",
                        required: true,
                        defaultValue: "default",
                        description: "the x slot"
                ]],
                deepLinkPath: "/bp/b1"
        ]

        when:
        def bp = BlueprintYamlLoader.fromMap(m)

        then:
        bp.id() == "b1"
        bp.title() == "Blueprint One"
        bp.tags() == ["security", "hourly"]
        bp.slots().size() == 1
        bp.slots()[0].key() == "x"
        bp.slots()[0].defaultValue() == "default"
    }

    def "fromMap falls back to TEXT for unknown slot type"() {
        given:
        Map<String, Object> m = [
                id: "b1",
                slots: [[key: "x", label: "X", type: "SOMETHING_WEIRD"]]
        ]

        when:
        def bp = BlueprintYamlLoader.fromMap(m)

        then:
        bp.slots()[0].type() == BlueprintSlot.SlotType.TEXT
    }

    def "loadDirectory ignores non-yaml files and unparseable ones"() {
        given:
        // Valid yaml
        Files.writeString(dir.resolve("ok.yml"), """
id: good
title: Good
""")
        // Garbage — will fail YAML parse
        Files.writeString(dir.resolve("bad.yml"), "{{{ not yaml")
        // Not YAML — should be ignored
        Files.writeString(dir.resolve("readme.md"), "# hi")

        when:
        def defs = BlueprintYamlLoader.loadDirectory(dir)

        then:
        defs*.id() == ["good"]
    }

    def "loadDirectory returns empty for non-existent path"() {
        expect:
        BlueprintYamlLoader.loadDirectory(dir.resolve("does-not-exist")) == []
    }

    def "loads the shipped sample blueprints from classpath"() {
        given:
        // Copy the samples to a temp dir since loadDirectory takes a filesystem Path
        def samplesUrl = getClass().classLoader.getResource("blueprints/samples")
        assert samplesUrl != null : "sample yaml resources missing"
        def samplesDir = Path.of(samplesUrl.toURI())

        when:
        def defs = BlueprintYamlLoader.loadDirectory(samplesDir)

        then:
        defs*.id().toSet() == ["daily-security-audit", "weekly-github-pr-review", "hourly-cost-check"].toSet()
        defs.every { !it.title().isBlank() && !it.category().isBlank() }
    }
}
