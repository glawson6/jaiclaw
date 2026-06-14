package io.jaiclaw.examples.agentmind

import io.jaiclaw.agentmind.soul.hook.SoulPromptInjector
import io.jaiclaw.agentmind.soul.personas.PersonaOverlayManager
import io.jaiclaw.agentmind.soul.personas.PersonalityAgentTool
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import spock.lang.Specification

/**
 * Plan task 4.4 — boot the demo and assert that all four AgentMind surfaces
 * (Soul + persona + memory + tendencies) wire as configured.
 *
 * Light-touch boot smoke spec: no LLM calls, no HTTP. Just verifies the
 * configuration translates into the expected beans.
 */
@SpringBootTest(
    classes = AgentMindDemoApplication,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.main.web-application-type=none",
        "spring.ai.anthropic.api-key=test-not-used",
        "jaiclaw.agentmind.soul.personas.dir=\${java.io.tmpdir}/agentmind-demo-spec-personas"
    ]
)
class AgentMindDemoBootSpec extends Specification {

    @Autowired
    ApplicationContext context

    def "soul pillar is wired"() {
        expect:
        context.containsBean("agentmindSoulModuleMarker")
        context.getBean(SoulPromptInjector) != null
    }

    def "persona overlay manager + tool are wired when personas.enabled=true"() {
        expect:
        context.getBean(PersonaOverlayManager) != null
        context.getBean(PersonalityAgentTool) != null
    }

    def "memory pillar marker bean is present"() {
        expect:
        context.containsBean("agentmindMemoryModuleMarker")
    }

    def "tendencies pillar marker bean is present"() {
        expect:
        context.containsBean("agentmindTendenciesModuleMarker")
    }
}
