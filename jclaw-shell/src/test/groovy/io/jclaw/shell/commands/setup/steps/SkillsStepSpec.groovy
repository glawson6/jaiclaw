package io.jclaw.shell.commands.setup.steps

import io.jclaw.shell.commands.setup.OnboardResult
import org.springframework.shell.component.context.BaseComponentContext
import org.springframework.shell.component.flow.ComponentFlow
import org.springframework.shell.component.flow.MultiItemSelectorSpec
import org.springframework.shell.component.flow.StringInputSpec
import spock.lang.Specification

class SkillsStepSpec extends Specification {

    ComponentFlow.Builder flowBuilder = Mock()

    SkillsStep step = new SkillsStep(flowBuilder)

    def "name returns Skills"() {
        expect:
        step.name() == "Skills"
    }

    def "quickstart mode sets all bundled skills enabled with no workspace dir"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.QUICKSTART)

        when:
        boolean success = step.execute(result)

        then:
        success
        result.skillsConfig() != null
        result.skillsConfig().enabledBundled() == ["*"]
        result.skillsConfig().workspaceDir() == null
        0 * flowBuilder._
    }

    def "manual mode collects skills and workspace dir"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.MANUAL)

        // Mock for multi-item selector flow
        def skillsContext = new BaseComponentContext()
        skillsContext.put("bundled-skills", ["coding", "web-research"])
        def skillsFlowResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> skillsContext
        }
        def skillsFlow = Mock(ComponentFlow) {
            run() >> skillsFlowResult
        }

        // Mock for workspace dir flow
        def dirContext = new BaseComponentContext()
        dirContext.put("workspace-skills-dir", "~/.jclaw/skills/")
        def dirFlowResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> dirContext
        }
        def dirFlow = Mock(ComponentFlow) {
            run() >> dirFlowResult
        }

        // Builder chain mocking — 2 clone() calls (skills selector, workspace dir)
        def clonedBuilder = Mock(ComponentFlow.Builder)
        def multiItemSelector = Mock(MultiItemSelectorSpec)
        def stringInput = Mock(StringInputSpec)
        def clonedBuilder2 = Mock(ComponentFlow.Builder)

        flowBuilder.clone() >>> [clonedBuilder, clonedBuilder2]
        clonedBuilder.reset() >> clonedBuilder
        clonedBuilder.withMultiItemSelector("bundled-skills") >> multiItemSelector
        multiItemSelector.name(_) >> multiItemSelector
        multiItemSelector.selectItems(_) >> multiItemSelector
        multiItemSelector.and() >> clonedBuilder
        clonedBuilder.build() >> skillsFlow

        clonedBuilder2.reset() >> clonedBuilder2
        clonedBuilder2.withStringInput("workspace-skills-dir") >> stringInput
        stringInput.name(_) >> stringInput
        stringInput.defaultValue(_) >> stringInput
        stringInput.and() >> clonedBuilder2
        clonedBuilder2.build() >> dirFlow

        when:
        boolean success = step.execute(result)

        then:
        success
        result.skillsConfig() != null
        result.skillsConfig().enabledBundled() == ["coding", "web-research"]
        result.skillsConfig().workspaceDir() == "~/.jclaw/skills/"
    }

    def "manual mode with all skills selected uses wildcard"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.MANUAL)

        def allSkills = ["coding", "web-research", "system-admin", "conversation", "summarize", "k8s-monitoring"]

        def skillsContext = new BaseComponentContext()
        skillsContext.put("bundled-skills", allSkills)
        def skillsFlowResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> skillsContext
        }
        def skillsFlow = Mock(ComponentFlow) {
            run() >> skillsFlowResult
        }

        def dirContext = new BaseComponentContext()
        dirContext.put("workspace-skills-dir", "")
        def dirFlowResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> dirContext
        }
        def dirFlow = Mock(ComponentFlow) {
            run() >> dirFlowResult
        }

        def clonedBuilder = Mock(ComponentFlow.Builder)
        def multiItemSelector = Mock(MultiItemSelectorSpec)
        def stringInput = Mock(StringInputSpec)
        def clonedBuilder2 = Mock(ComponentFlow.Builder)

        flowBuilder.clone() >>> [clonedBuilder, clonedBuilder2]
        clonedBuilder.reset() >> clonedBuilder
        clonedBuilder.withMultiItemSelector("bundled-skills") >> multiItemSelector
        multiItemSelector.name(_) >> multiItemSelector
        multiItemSelector.selectItems(_) >> multiItemSelector
        multiItemSelector.and() >> clonedBuilder
        clonedBuilder.build() >> skillsFlow

        clonedBuilder2.reset() >> clonedBuilder2
        clonedBuilder2.withStringInput("workspace-skills-dir") >> stringInput
        stringInput.name(_) >> stringInput
        stringInput.defaultValue(_) >> stringInput
        stringInput.and() >> clonedBuilder2
        clonedBuilder2.build() >> dirFlow

        when:
        boolean success = step.execute(result)

        then:
        success
        result.skillsConfig().enabledBundled() == ["*"]
        result.skillsConfig().workspaceDir() == null
    }
}
