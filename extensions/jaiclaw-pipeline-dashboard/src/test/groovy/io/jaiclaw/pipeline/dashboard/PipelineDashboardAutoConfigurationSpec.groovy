package io.jaiclaw.pipeline.dashboard

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import spock.lang.Specification

/**
 * Autoconfig contract for the read-only Pipeline Dashboard.
 * Uses the {@link AnnotationConfigApplicationContext} pattern established
 * in the {@code jaiclaw-session-redis} module — see the "Groovy 5 /
 * Spring Boot 4" note in the studio plan.
 */
class PipelineDashboardAutoConfigurationSpec extends Specification {

    private AnnotationConfigApplicationContext ctxWith(Map<String, Object> env) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()
        if (env != null && !env.isEmpty()) {
            ctx.environment.propertySources.addFirst(new MapPropertySource("test", env))
        }
        return ctx
    }

    def "activates by default (property missing) — beans register"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith([:] as Map<String, Object>)
        ctx.register(PipelineDashboardAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBean(PipelineDashboardController)
        ctx.getBean(WebMvcConfigurer)

        cleanup:
        ctx.close()
    }

    def "explicit enabled=true activates"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith(
                ["jaiclaw.pipeline.dashboard.enabled": "true"] as Map<String, Object>)
        ctx.register(PipelineDashboardAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBean(PipelineDashboardController)

        cleanup:
        ctx.close()
    }

    def "enabled=false suppresses the beans"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith(
                ["jaiclaw.pipeline.dashboard.enabled": "false"] as Map<String, Object>)
        ctx.register(PipelineDashboardAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBeanNamesForType(PipelineDashboardController).length == 0

        cleanup:
        ctx.close()
    }
}
