package io.jaiclaw.pipeline.loader;

import io.jaiclaw.pipeline.PipelineDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a list of Spring resource patterns (e.g.
 * {@code classpath*:jaiclaw/pipelines/*.yml},
 * {@code file:${HOME}/.jaiclaw/pipelines/*.yml}) into a list of
 * {@link PipelineDefinition}s. Parse failures on a single file are logged at
 * WARN and the loader moves on — one bad file mustn't take the whole app
 * down at startup.
 *
 * <p>Idempotent: each file resolves to one pipeline; subsequent files with
 * the same id override earlier ones in encounter order.
 */
public class PipelineFileLoader {

    private static final Logger log = LoggerFactory.getLogger(PipelineFileLoader.class);

    private final ResourcePatternResolver resolver;

    public PipelineFileLoader(ResourceLoader resourceLoader) {
        this.resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    /**
     * Resolve every pattern, parse each matching resource, return the result
     * in encounter order. Duplicate resources (same URI across patterns) are
     * loaded once.
     */
    public List<PipelineDefinition> loadAll(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return List.of();
        }
        List<PipelineDefinition> out = new ArrayList<>();
        Set<String> seenUris = new LinkedHashSet<>();
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            Resource[] resources;
            try {
                resources = resolver.getResources(pattern);
            } catch (IOException e) {
                log.warn("Pipeline location '{}' could not be resolved: {}", pattern, e.getMessage());
                continue;
            }
            for (Resource resource : resources) {
                String uri = resourceUri(resource);
                if (!seenUris.add(uri)) continue;
                PipelineDefinition definition = tryParse(resource, uri);
                if (definition != null) {
                    out.add(definition);
                }
            }
        }
        return out;
    }

    private PipelineDefinition tryParse(Resource resource, String uri) {
        String fallbackId = fallbackIdFor(resource);
        try (InputStream in = resource.getInputStream()) {
            PipelineDefinition definition = PipelineYamlParser.parse(in, fallbackId, uri);
            log.debug("Parsed pipeline '{}' from {}", definition.id(), uri);
            return definition;
        } catch (PipelineLoadException e) {
            log.warn("Skipping pipeline file {}: {}", uri, e.getMessage());
            return null;
        } catch (IOException e) {
            log.warn("Failed to read pipeline file {}: {}", uri, e.getMessage());
            return null;
        }
    }

    /** Resource URI used for de-dup + error messages. */
    private static String resourceUri(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (IOException ignored) {
            // Resources without a URI (rare) fall back to description.
            return resource.getDescription();
        }
    }

    /**
     * Derive a fallback id from a resource's filename: strip the extension,
     * preserve hyphens. Anything we can't infer becomes {@code "unnamed-pipeline"}
     * — that will collide with the next un-named file, which is exactly the
     * sort of misconfiguration we want users to notice immediately.
     */
    static String fallbackIdFor(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || filename.isBlank()) {
            return "unnamed-pipeline";
        }
        int dot = filename.lastIndexOf('.');
        String stem = (dot > 0) ? filename.substring(0, dot) : filename;
        return stem.isBlank() ? "unnamed-pipeline" : stem;
    }
}
