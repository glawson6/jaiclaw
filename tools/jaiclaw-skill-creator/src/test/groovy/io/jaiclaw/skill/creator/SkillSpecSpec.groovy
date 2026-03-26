package io.jaiclaw.skill.creator

import spock.lang.Specification

class SkillSpecSpec extends Specification {

    def "fromYamlMap parses all fields"() {
        given:
        def map = [
                name        : 'my-skill',
                description : 'Does X when Y',
                platforms   : ['darwin', 'linux'],
                requiredBins: ['docker'],
                purpose     : 'Multi-line purpose'
        ]

        when:
        def spec = SkillSpec.fromYamlMap(map)

        then:
        spec.name() == 'my-skill'
        spec.description() == 'Does X when Y'
        spec.platforms() == ['darwin', 'linux']
        spec.requiredBins() == ['docker']
        spec.purpose() == 'Multi-line purpose'
    }

    def "fromYamlMap handles missing optional fields"() {
        given:
        def map = [
                name       : 'simple-skill',
                description: 'A simple skill',
                purpose    : 'Does something'
        ]

        when:
        def spec = SkillSpec.fromYamlMap(map)

        then:
        spec.name() == 'simple-skill'
        spec.platforms() == []
        spec.requiredBins() == []
    }

    def "validate passes for valid spec"() {
        given:
        def spec = new SkillSpec('my-skill', 'desc', ['darwin'], [], 'purpose')

        when:
        spec.validate()

        then:
        noExceptionThrown()
    }

    def "validate rejects missing #expectedMsg"() {
        given:
        def spec = new SkillSpec(name, desc, [], [], purpose)

        when:
        spec.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(expectedMsg)

        where:
        name       | desc   | purpose   | expectedMsg
        null       | 'desc' | 'purpose' | 'name'
        ''         | 'desc' | 'purpose' | 'name'
        'my-skill' | null   | 'purpose' | 'description'
        'my-skill' | ''     | 'purpose' | 'description'
        'my-skill' | 'desc' | null      | 'purpose'
        'my-skill' | 'desc' | ''        | 'purpose'
    }

    def "validate rejects invalid name format '#name'"() {
        given:
        def spec = new SkillSpec(name, 'desc', [], [], 'purpose')

        when:
        spec.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid skill name')

        where:
        name << ['My-Skill', 'my skill', 'my_skill', '-leading', 'trailing-']
    }

    def "toPrompt includes all fields"() {
        given:
        def spec = new SkillSpec('my-skill', 'Does X', ['darwin', 'linux'], ['docker'], 'The purpose')

        when:
        def prompt = spec.toPrompt()

        then:
        prompt.contains('my-skill')
        prompt.contains('Does X')
        prompt.contains('darwin, linux')
        prompt.contains('docker')
        prompt.contains('The purpose')
    }

    def "toPrompt omits empty optional fields"() {
        given:
        def spec = new SkillSpec('my-skill', 'Does X', [], [], 'The purpose')

        when:
        def prompt = spec.toPrompt()

        then:
        !prompt.contains('Platforms:')
        !prompt.contains('Required binaries:')
    }
}
