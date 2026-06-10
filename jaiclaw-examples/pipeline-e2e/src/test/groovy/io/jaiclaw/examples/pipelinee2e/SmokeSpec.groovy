package io.jaiclaw.examples.pipelinee2e

import io.jaiclaw.pipeline.PipelineRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

/**
 * Boot-context smoke test — just confirms the example app's Spring context
 * starts and registers `processor-pipe`. Heavy HTTP-surface verification
 * lives in `extensions/jaiclaw-pipeline`'s `PipelineHttpIntegrationSpec`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = [
        "management.endpoints.web.exposure.include=health",
        "jaiclaw.skills.allow-bundled="
])
class SmokeSpec extends Specification {

    @Autowired
    PipelineRegistry registry

    def "processor-pipe is registered"() {
        expect:
        registry.contains("processor-pipe")
    }
}
