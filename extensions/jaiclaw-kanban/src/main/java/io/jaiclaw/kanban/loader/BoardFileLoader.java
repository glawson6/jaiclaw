package io.jaiclaw.kanban.loader;

import io.jaiclaw.kanban.model.BoardDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a list of Spring resource patterns (e.g.
 * {@code classpath*:jaiclaw/kanban/*.yml}, {@code file:${HOME}/.jaiclaw/kanban/boards/*.yml})
 * into a list of {@link BoardDefinition}s. Parse failures on a single file
 * are logged at WARN and the loader moves on — one bad file mustn't take the
 * whole app down at startup.
 *
 * <p>Mirrors {@code PipelineFileLoader} from {@code jaiclaw-pipeline}.
 */
public class BoardFileLoader {

    private static final Logger log = LoggerFactory.getLogger(BoardFileLoader.class);

    private final ResourcePatternResolver resolver;

    public BoardFileLoader(ResourceLoader resourceLoader) {
        this.resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    public List<BoardDefinition> loadAll(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return List.of();
        }
        List<BoardDefinition> out = new ArrayList<>();
        Set<String> seenUris = new LinkedHashSet<>();
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            String expanded = expandHome(pattern);
            Resource[] resources;
            try {
                resources = resolver.getResources(expanded);
            } catch (IOException e) {
                log.warn("Kanban board location '{}' could not be resolved: {}",
                        expanded, e.getMessage());
                continue;
            }
            for (Resource resource : resources) {
                String uri = resourceUri(resource);
                if (!seenUris.add(uri)) continue;
                BoardDefinition definition = tryParse(resource, uri);
                if (definition != null) {
                    out.add(definition);
                }
            }
        }
        return out;
    }

    private BoardDefinition tryParse(Resource resource, String uri) {
        String fallbackId = fallbackIdFor(resource);
        try (InputStream in = resource.getInputStream()) {
            BoardDefinition def = BoardYamlParser.parse(in, fallbackId, uri);
            log.debug("Parsed board '{}' from {}", def.id(), uri);
            return def;
        } catch (BoardLoadException e) {
            log.warn("Skipping board file {}: {}", uri, e.getMessage());
            return null;
        } catch (IOException e) {
            log.warn("Failed to read board file {}: {}", uri, e.getMessage());
            return null;
        }
    }

    private static String resourceUri(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (IOException ignored) {
            return resource.getDescription();
        }
    }

    static String fallbackIdFor(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || filename.isBlank()) {
            return "unnamed-board";
        }
        int dot = filename.lastIndexOf('.');
        String stem = (dot > 0) ? filename.substring(0, dot) : filename;
        return stem.isBlank() ? "unnamed-board" : stem;
    }

    /** Expand a leading {@code ~} (single-tenant convenience for user-home patterns). */
    static String expandHome(String pattern) {
        if (pattern.startsWith("file:~/")) {
            return "file:" + System.getProperty("user.home") + pattern.substring(5);
        }
        if (pattern.startsWith("~/")) {
            return System.getProperty("user.home") + pattern.substring(1);
        }
        return pattern;
    }
}
