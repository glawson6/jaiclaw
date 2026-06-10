package io.jaiclaw.pipeline.loader

import io.jaiclaw.pipeline.PipelineDefinition
import org.springframework.core.io.DefaultResourceLoader
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PipelineFileLoaderSpec extends Specification {

    @TempDir
    Path tempDir

    PipelineFileLoader loader = new PipelineFileLoader(new DefaultResourceLoader())

    private Path writeYaml(String filename, String body) {
        Path p = tempDir.resolve(filename)
        Files.writeString(p, body)
        return p
    }

    def "loads pipelines from a file: glob"() {
        given:
        writeYaml("one.yml", """
id: pipe-one
stages:
  - { name: s, type: PROCESSOR, bean: b }
""")
        writeYaml("two.yml", """
id: pipe-two
stages:
  - { name: s, type: PROCESSOR, bean: b }
""")
        String pattern = "file:" + tempDir.toString() + "/*.yml"

        when:
        List<PipelineDefinition> defs = loader.loadAll([pattern])

        then:
        defs.size() == 2
        defs*.id().toSorted() == ["pipe-one", "pipe-two"]
    }

    def "falls back to filename stem when id is missing"() {
        given:
        writeYaml("file-name-pipe.yml", """
name: My Pipeline
stages:
  - { name: s, type: PROCESSOR, bean: b }
""")
        String pattern = "file:" + tempDir.toString() + "/file-name-pipe.yml"

        when:
        List<PipelineDefinition> defs = loader.loadAll([pattern])

        then:
        defs.size() == 1
        defs[0].id() == "file-name-pipe"
    }

    def "skips a malformed file and continues with the rest"() {
        given:
        writeYaml("bad.yml", "this: is: invalid: yaml: ::::")
        writeYaml("good.yml", """
id: good
stages:
  - { name: s, type: PROCESSOR, bean: b }
""")
        String pattern = "file:" + tempDir.toString() + "/*.yml"

        when:
        List<PipelineDefinition> defs = loader.loadAll([pattern])

        then:
        defs.size() == 1
        defs[0].id() == "good"
    }

    def "dedupes the same resource across multiple patterns"() {
        given:
        writeYaml("dup.yml", """
id: only-once
stages:
  - { name: s, type: PROCESSOR, bean: b }
""")
        String p1 = "file:" + tempDir.toString() + "/dup.yml"
        String p2 = "file:" + tempDir.toString() + "/*.yml"

        when:
        List<PipelineDefinition> defs = loader.loadAll([p1, p2])

        then:
        defs.size() == 1
        defs[0].id() == "only-once"
    }

    def "returns empty list when patterns list is null or empty"() {
        expect:
        loader.loadAll(null).isEmpty()
        loader.loadAll([]).isEmpty()
    }

    def "tolerates a pattern that resolves to zero resources"() {
        when:
        List<PipelineDefinition> defs = loader.loadAll([
                "file:" + tempDir.toString() + "/nonexistent/*.yml",
                "classpath*:jaiclaw/pipelines/never-exists-*.yml"
        ])

        then:
        defs.isEmpty()
    }

    def "fallbackIdFor strips extension"() {
        expect:
        PipelineFileLoader.fallbackIdFor(new org.springframework.core.io.FileSystemResource("/x/y/foo.yml")) == "foo"
        PipelineFileLoader.fallbackIdFor(new org.springframework.core.io.FileSystemResource("/x/y/foo-bar.yaml")) == "foo-bar"
    }
}
