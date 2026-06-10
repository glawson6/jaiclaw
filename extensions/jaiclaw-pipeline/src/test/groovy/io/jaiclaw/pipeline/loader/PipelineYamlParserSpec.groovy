package io.jaiclaw.pipeline.loader

import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerType
import spock.lang.Specification

class PipelineYamlParserSpec extends Specification {

    private static PipelineDefinition parse(String yaml, String fallbackId = "fallback", String name = "spec.yml") {
        return PipelineYamlParser.parse(new ByteArrayInputStream(yaml.getBytes("UTF-8")), fallbackId, name)
    }

    def "parses a complete pipeline document"() {
        given:
        String yaml = """
id: research-pipe
name: Research Pipeline
description: Demo pipeline
enabled: true
trigger:
  type: MANUAL
stages:
  - name: research
    type: PROCESSOR
    bean: doSomething
  - name: write
    type: PROCESSOR
    bean: writeIt
    systemPrompt: "use {{stages.research.output}}"
output:
  type: LOG
  template: "result: {{stages.write.output}}"
"""

        when:
        PipelineDefinition d = parse(yaml)

        then:
        d.id() == "research-pipe"
        d.name() == "Research Pipeline"
        d.enabled()
        d.trigger().type() == TriggerType.MANUAL
        d.stages().size() == 2
        d.stages()[0].name() == "research"
        d.stages()[0].type() == StageType.PROCESSOR
        d.stages()[1].systemPrompt().contains("research.output")
        d.output().type() == OutputType.LOG
    }

    def "supplies the fallback id when 'id' is absent"() {
        given:
        String yaml = """
name: No-Id Pipeline
stages:
  - name: s1
    type: PROCESSOR
    bean: b
"""

        when:
        PipelineDefinition d = parse(yaml, "from-filename")

        then:
        d.id() == "from-filename"
    }

    def "defaults enabled to true when the field is absent"() {
        given:
        String yaml = """
id: defaults-pipe
stages:
  - { name: s, type: PROCESSOR, bean: b }
"""

        when:
        PipelineDefinition d = parse(yaml)

        then:
        d.enabled()
    }

    def "respects explicit enabled: false"() {
        given:
        String yaml = """
id: off-pipe
enabled: false
stages:
  - { name: s, type: PROCESSOR, bean: b }
"""

        when:
        PipelineDefinition d = parse(yaml)

        then:
        !d.enabled()
    }

    def "supplies the fallback id when 'id' is blank"() {
        given:
        String yaml = """
id: "  "
stages:
  - { name: s1, type: PROCESSOR, bean: b }
"""

        when:
        PipelineDefinition d = parse(yaml, "from-filename")

        then:
        d.id() == "from-filename"
    }

    def "respects the file's id when present"() {
        given:
        String yaml = """
id: explicit-id
stages:
  - { name: s1, type: PROCESSOR, bean: b }
"""

        when:
        PipelineDefinition d = parse(yaml, "from-filename")

        then:
        d.id() == "explicit-id"
    }

    def "throws PipelineLoadException with the resource name on malformed YAML"() {
        given:
        String yaml = "this: is: not: yaml: ::::"

        when:
        parse(yaml, "x", "broken.yml")

        then:
        PipelineLoadException ex = thrown()
        ex.message.contains("broken.yml")
    }

    def "throws on empty input"() {
        when:
        parse("   ", "x", "empty.yml")

        then:
        PipelineLoadException ex = thrown()
        ex.message.contains("empty.yml")
        ex.message.toLowerCase().contains("empty")
    }

    def "throws when YAML root is not an object"() {
        given:
        String yaml = "- 1\n- 2\n- 3"

        when:
        parse(yaml, "x", "list.yml")

        then:
        PipelineLoadException ex = thrown()
        ex.message.contains("list.yml")
        ex.message.contains("object")
    }

    def "rejects blank fallback id"() {
        when:
        parse("name: x", "  ", "file.yml")

        then:
        PipelineLoadException ex = thrown()
        ex.message.contains("fallback id")
    }
}
