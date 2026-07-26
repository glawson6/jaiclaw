package io.jaiclaw.autoconfigure;

import io.jaiclaw.asciirender.skill.RenderableTemplate;
import io.jaiclaw.asciirender.skill.RenderableTemplateRegistry;
import io.jaiclaw.tools.builtin.render.RenderResponseTool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Wires the framework-provided {@code render_response} tool + its
 * {@link RenderableTemplateRegistry}. Adopters register one
 * {@link RenderableTemplate} {@code @Bean} per domain shape they want
 * to render (event card, task kanban, etc.) — the registry collects
 * them and the tool dispatches LLM calls to the right one.
 *
 * <p><strong>Enabled by default</strong> when {@code jaiclaw-ascii-render}
 * is on the classpath (which it is transitively via {@code jaiclaw-tools}).
 * Opt out entirely with:
 * <pre>
 * jaiclaw:
 *   tools:
 *     render-response:
 *       enabled: false
 * </pre>
 *
 * <p>When no {@code RenderableTemplate} beans are registered, the
 * registry loads with 0 templates and the tool errors on every call
 * with "no templates registered" — the LLM sees an empty
 * {@code template.enum} in the tool's schema and skips the tool.
 * Functionally identical to opt-out.
 *
 * <p>Companion pieces: the {@code object-rendering} bundled skill in
 * {@code jaiclaw-ascii-render}'s resources ships the LLM-side fidelity
 * rules that make the whole loop reliable.
 */
@AutoConfiguration
@ConditionalOnClass(RenderableTemplate.class)
@ConditionalOnProperty(
        prefix = "jaiclaw.tools.render-response",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RenderableTemplatesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RenderableTemplateRegistry renderableTemplateRegistry(
            ObjectProvider<RenderableTemplate> templates) {
        return new RenderableTemplateRegistry(templates.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public RenderResponseTool renderResponseTool(RenderableTemplateRegistry registry) {
        return new RenderResponseTool(registry);
    }
}
