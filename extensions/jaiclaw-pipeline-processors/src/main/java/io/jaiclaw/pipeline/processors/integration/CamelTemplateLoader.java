package io.jaiclaw.pipeline.processors.integration;

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
 * Loads {@link CamelTemplate}s from classpath YAML resources at
 * {@code META-INF/jaiclaw-pipeline-camel-templates/*.yml}. Downstream
 * apps can add their own templates by dropping YAMLs into the same
 * classpath location.
 */
public class CamelTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(CamelTemplateLoader.class);
    private static final String LOCATION_PATTERN =
            "classpath*:META-INF/jaiclaw-pipeline-camel-templates/*.yml";

    private final List<CamelTemplate> templates;

    public CamelTemplateLoader() {
        this.templates = loadFromClasspath();
    }

    public List<CamelTemplate> templates() {
        return Collections.unmodifiableList(templates);
    }

    private static List<CamelTemplate> loadFromClasspath() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        ObjectMapper yaml = YAMLMapper.builder().build();
        List<CamelTemplate> out = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(LOCATION_PATTERN);
            for (Resource r : resources) {
                try (InputStream in = r.getInputStream()) {
                    CamelTemplate template = yaml.readValue(in, CamelTemplate.class);
                    out.add(template);
                } catch (Exception e) {
                    log.warn("Skipping unreadable Camel template {}: {}",
                            r.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to enumerate Camel templates on the classpath: {}",
                    e.getMessage());
        }
        out.sort((a, b) -> a.id().compareTo(b.id()));
        log.info("Loaded {} CamelTemplate(s) from classpath", out.size());
        return out;
    }
}
