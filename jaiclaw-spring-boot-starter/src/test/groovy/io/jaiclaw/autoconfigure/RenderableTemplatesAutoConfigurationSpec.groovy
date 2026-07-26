package io.jaiclaw.autoconfigure

import io.jaiclaw.asciirender.skill.RenderableTemplate
import io.jaiclaw.asciirender.skill.RenderableTemplateRegistry
import io.jaiclaw.tools.builtin.render.RenderResponseTool
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Locks the wiring shape of {@link RenderableTemplatesAutoConfiguration} so
 * a future refactor can't silently drop the {@code render_response} tool or
 * the {@code RenderableTemplateRegistry} bean factory.
 *
 * <p>Metadata-based (no live Spring context boot) — matches the
 * {@code CamelChannelHandlerDisambiguationSpec} pattern for autoconfig lock
 * specs in this module. A live-context boot would need
 * {@code @ImportAutoConfiguration} exclusions for the full transitive
 * autoconfig chain and adds fragility for the amount of coverage gained.
 * Behavior is exercised by {@code RenderResponseToolSpec} (constructor +
 * dispatch) + {@code RenderableTemplateRegistrySpec} (collection semantics)
 * in their respective modules; this spec locks the wiring only.
 */
class RenderableTemplatesAutoConfigurationSpec extends Specification {

    def "class is annotated as @AutoConfiguration"() {
        expect:
        RenderableTemplatesAutoConfiguration.getAnnotation(AutoConfiguration) != null
    }

    def "@ConditionalOnClass gates on RenderableTemplate presence"() {
        given:
        def ann = RenderableTemplatesAutoConfiguration.getAnnotation(ConditionalOnClass)

        expect: "guards against the module being absent"
        ann != null
        ann.value() == [RenderableTemplate.class] as Class[]
    }

    def "@ConditionalOnProperty enables by default; opt out via jaiclaw.tools.render-response.enabled=false"() {
        given:
        def ann = RenderableTemplatesAutoConfiguration.getAnnotation(ConditionalOnProperty)

        expect:
        ann != null
        ann.prefix() == "jaiclaw.tools.render-response"
        ann.name() == ["enabled"] as String[]
        ann.havingValue() == "true"
        ann.matchIfMissing()
    }

    def "renderableTemplateRegistry @Bean factory takes ObjectProvider<RenderableTemplate>"() {
        given:
        Method method = RenderableTemplatesAutoConfiguration
                .getDeclaredMethod("renderableTemplateRegistry", ObjectProvider)

        expect:
        method.getAnnotation(Bean) != null
        method.getAnnotation(ConditionalOnMissingBean) != null
        method.returnType == RenderableTemplateRegistry
    }

    def "renderResponseTool @Bean factory takes RenderableTemplateRegistry"() {
        given:
        Method method = RenderableTemplatesAutoConfiguration
                .getDeclaredMethod("renderResponseTool", RenderableTemplateRegistry)

        expect:
        method.getAnnotation(Bean) != null
        method.getAnnotation(ConditionalOnMissingBean) != null
        method.returnType == RenderResponseTool
    }

    def "listed in AutoConfiguration.imports so Spring Boot picks it up"() {
        given:
        String imports = getClass()
                .getResourceAsStream("/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                .text

        expect:
        imports.contains("io.jaiclaw.autoconfigure.RenderableTemplatesAutoConfiguration")
    }
}
