package io.jaiclaw.pipeline.studio

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import spock.lang.Specification

/**
 * Verifies the tiny WebMVC resource-handler contract the SPA jar
 * ships. Uses the AnnotationConfigApplicationContext + MapPropertySource
 * pattern established in the jaiclaw-session-redis module — Groovy 5
 * fails to dispatch on ApplicationContextRunner's SELF-typed varargs
 * builders.
 */
class PipelineStudioSpaAutoConfigurationSpec extends Specification {

    private AnnotationConfigApplicationContext ctxWith(Map<String, Object> env) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()
        if (env != null && !env.isEmpty()) {
            ctx.environment.propertySources.addFirst(new MapPropertySource("test", env))
        }
        return ctx
    }

    def "registers the SPA WebMvcConfigurer by default"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith([:] as Map<String, Object>)
        ctx.register(PipelineStudioSpaAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBean(WebMvcConfigurer)

        cleanup:
        ctx.close()
    }

    def "explicit enabled=true still activates"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith(
                ["jaiclaw.pipeline.studio.spa.enabled": "true"] as Map<String, Object>)
        ctx.register(PipelineStudioSpaAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBean(WebMvcConfigurer)

        cleanup:
        ctx.close()
    }

    def "enabled=false suppresses the configurer"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith(
                ["jaiclaw.pipeline.studio.spa.enabled": "false"] as Map<String, Object>)
        ctx.register(PipelineStudioSpaAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBeanNamesForType(WebMvcConfigurer).length == 0

        cleanup:
        ctx.close()
    }
}
