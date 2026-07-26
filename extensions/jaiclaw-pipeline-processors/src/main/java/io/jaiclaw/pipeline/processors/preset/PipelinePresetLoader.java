package io.jaiclaw.pipeline.processors.preset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads {@link AgentPreset}s from classpath YAML resources at
 * {@code META-INF/jaiclaw-pipeline-presets/*.yml}. Every consuming
 * app can drop additional YAMLs into its own classpath location
 * matching that pattern to add presets — no code required.
 *
 * <p>Load happens once at bean-construction time. Adopters wanting
 * hot-reload of preset files can rebuild the bean at runtime; the
 * loader itself is stateless past construction.
 */
public class PipelinePresetLoader {

    private static final Logger log = LoggerFactory.getLogger(PipelinePresetLoader.class);
    private static final String LOCATION_PATTERN = "classpath*:META-INF/jaiclaw-pipeline-presets/*.yml";

    private final List<AgentPreset> presets;

    public PipelinePresetLoader() {
        this.presets = loadFromClasspath();
    }

    public List<AgentPreset> presets() {
        return Collections.unmodifiableList(presets);
    }

    private static List<AgentPreset> loadFromClasspath() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        ObjectMapper yaml = YAMLMapper.builder().build();
        List<AgentPreset> out = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(LOCATION_PATTERN);
            for (Resource r : resources) {
                try (InputStream in = r.getInputStream()) {
                    AgentPreset preset = yaml.readValue(in, AgentPreset.class);
                    out.add(preset);
                } catch (Exception e) {
                    log.warn("Skipping unreadable preset {}: {}", r.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to enumerate presets on the classpath: {}", e.getMessage());
        }
        out.sort((a, b) -> a.id().compareTo(b.id()));
        log.info("Loaded {} AgentPreset(s) from classpath", out.size());
        return out;
    }
}
