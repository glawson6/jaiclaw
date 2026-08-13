package io.jaiclaw.tools.builtin.ascii;

import io.jaiclaw.asciirender.glyph.AnsiPalette;
import io.jaiclaw.asciirender.glyph.GlyphContribution;
import io.jaiclaw.asciirender.glyph.GlyphDefinition;
import io.jaiclaw.asciirender.glyph.GlyphRegistry;
import io.jaiclaw.asciirender.glyph.GlyphSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Merges built-in {@link io.jaiclaw.asciirender.glyph.GlyphSet}
 * defaults with every {@link GlyphContribution} bean found in the
 * Spring context, exposes the composite as the {@link GlyphRegistry}
 * bean, and installs it as {@link GlyphRegistry#global()} so element
 * builders that resolve {@code {"type":"glyph","params":{"name":"ok"}}}
 * see the merged vocabulary regardless of construction path.
 *
 * <p>Same "collect adopter beans + merge with defaults" pattern used
 * by {@code RenderableTemplateRegistry} — see its javadoc for the
 * design rationale.
 *
 * <p>Adopters that want a different {@link AnsiPalette} declare their
 * own {@code @Bean AnsiPalette}; the {@link ConditionalOnMissingBean}
 * on this class's palette bean makes it back off cleanly.
 */
@AutoConfiguration
public class GlyphRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GlyphRegistryAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public GlyphRegistry jaiclawGlyphRegistry(List<GlyphContribution> contributions) {
        GlyphRegistry registry = GlyphRegistry.defaults();
        int contributionCount = 0;
        int glyphCount = 0;
        if (contributions != null) {
            for (GlyphContribution contribution : contributions) {
                if (contribution == null) continue;
                List<GlyphDefinition> glyphs = contribution.glyphs();
                if (glyphs == null || glyphs.isEmpty()) continue;
                contributionCount++;
                for (GlyphDefinition g : glyphs) {
                    if (g == null) continue;
                    registry.register(g);
                    glyphCount++;
                }
            }
        }
        GlyphRegistry.setGlobal(registry);
        log.info("GlyphRegistry: {} built-in + {} adopter glyph(s) from {} contribution(s); {} names total",
                GlyphSet.defaults().size(), glyphCount, contributionCount, registry.size());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public AnsiPalette jaiclawAnsiPalette() {
        return AnsiPalette.defaultPalette();
    }
}
