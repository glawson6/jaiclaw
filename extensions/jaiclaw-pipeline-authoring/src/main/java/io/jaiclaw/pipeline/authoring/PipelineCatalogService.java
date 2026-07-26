package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.ErrorStrategy;
import io.jaiclaw.pipeline.OutputType;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageType;
import io.jaiclaw.pipeline.TriggerType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Assembles the JSON tree consumed by the Pipeline Studio's palette.
 * A single {@link #catalog()} call returns everything the frontend
 * needs to draw the palette + inspector without a second round-trip:
 *
 * <ul>
 *   <li>Trigger types, stage types, output types, error strategies —
 *       enumerated from the engine's enums so a new enum value
 *       shows up in the palette automatically.</li>
 *   <li>Registered processors — every Spring bean annotated with
 *       {@link PipelineProcessor} + implementing
 *       {@link ConfigurableStageProcessor}. Each entry carries name,
 *       category, description, icon, JSON-schema {@code configSchema}.</li>
 *   <li>Bare {@code Function<String,String>} bean names — surfaced as
 *       "custom bean" palette entries (name only, no config
 *       inspector).</li>
 *   <li>Channel ids — from {@link ChannelRegistry} when available.</li>
 *   <li>Camel template placeholders — empty in Phase 1; Phase 4
 *       populates them via classpath YAML resources.</li>
 * </ul>
 */
public class PipelineCatalogService {

    private final ApplicationContext applicationContext;
    private final ObjectProvider<ChannelRegistry> channelRegistryProvider;

    public PipelineCatalogService(ApplicationContext applicationContext,
                                   ObjectProvider<ChannelRegistry> channelRegistryProvider) {
        this.applicationContext = applicationContext;
        this.channelRegistryProvider = channelRegistryProvider;
    }

    /**
     * Return the assembled catalog. Recomputed on every call — cheap
     * (bean scan is O(bean-count)) and always reflects the current
     * Spring context.
     */
    public Map<String, Object> catalog() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("triggerTypes", enumNames(TriggerType.class));
        root.put("stageTypes", enumNames(StageType.class));
        root.put("outputTypes", enumNames(OutputType.class));
        root.put("errorStrategies", enumNames(ErrorStrategy.class));
        root.put("processors", discoverProcessors());
        root.put("customBeans", discoverFunctionBeanNames());
        root.put("channels", discoverChannels());
        root.put("presets", discoverPresets());
        root.put("cameltemplates", discoverCamelTemplates());
        return root;
    }

    /**
     * Discover AI presets registered by {@code jaiclaw-pipeline-processors}.
     * Uses class-name lookup instead of a compile-time dep so the
     * authoring module doesn't need to depend on processors. Returns
     * an empty list when the processors module is absent.
     */
    @SuppressWarnings("unchecked")
    private List<Object> discoverPresets() {
        if (applicationContext == null) return List.of();
        try {
            Class<?> loaderType = Class.forName(
                    "io.jaiclaw.pipeline.processors.preset.PipelinePresetLoader");
            Object loader = applicationContext.getBean(loaderType);
            java.lang.reflect.Method presetsMethod = loaderType.getMethod("presets");
            List<Object> raw = (List<Object>) presetsMethod.invoke(loader);
            return raw == null ? List.of() : List.copyOf(raw);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Discover curated Camel templates registered by
     * {@code jaiclaw-pipeline-processors}. Same reflection approach as
     * {@link #discoverPresets()}.
     */
    @SuppressWarnings("unchecked")
    private List<Object> discoverCamelTemplates() {
        if (applicationContext == null) return List.of();
        try {
            Class<?> loaderType = Class.forName(
                    "io.jaiclaw.pipeline.processors.integration.CamelTemplateLoader");
            Object loader = applicationContext.getBean(loaderType);
            java.lang.reflect.Method templatesMethod = loaderType.getMethod("templates");
            List<Object> raw = (List<Object>) templatesMethod.invoke(loader);
            return raw == null ? List.of() : List.copyOf(raw);
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── discovery ────────────────────────────────────

    private List<Map<String, Object>> discoverProcessors() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (applicationContext == null) return out;
        String[] beanNames = applicationContext.getBeanNamesForAnnotation(PipelineProcessor.class);
        for (String beanName : beanNames) {
            Class<?> type = applicationContext.getType(beanName);
            if (type == null) continue;
            PipelineProcessor meta = applicationContext.findAnnotationOnBean(
                    beanName, PipelineProcessor.class);
            if (meta == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("beanName", beanName);
            entry.put("name", meta.name());
            entry.put("category", meta.category());
            entry.put("description", meta.description());
            entry.put("icon", meta.icon());
            if (ConfigurableStageProcessor.class.isAssignableFrom(type)) {
                try {
                    Object bean = applicationContext.getBean(beanName);
                    if (bean instanceof ConfigurableStageProcessor cfg) {
                        entry.put("configSchema", cfg.configSchema());
                    }
                } catch (RuntimeException e) {
                    // Skip schema on lookup failure; palette still shows the node.
                }
            }
            out.add(entry);
        }
        out.sort((a, b) -> {
            String ca = String.valueOf(a.getOrDefault("category", ""));
            String cb = String.valueOf(b.getOrDefault("category", ""));
            int cmp = ca.compareTo(cb);
            if (cmp != 0) return cmp;
            return String.valueOf(a.getOrDefault("name", ""))
                    .compareTo(String.valueOf(b.getOrDefault("name", "")));
        });
        return out;
    }

    private List<String> discoverFunctionBeanNames() {
        if (applicationContext == null) return List.of();
        try {
            String[] names = applicationContext.getBeanNamesForType(Function.class);
            List<String> filtered = new ArrayList<>();
            for (String name : names) {
                Class<?> type = applicationContext.getType(name);
                // Skip beans already surfaced through the ConfigurableStageProcessor
                // discovery — those got the full metadata treatment above.
                if (type != null && ConfigurableStageProcessor.class.isAssignableFrom(type)) continue;
                filtered.add(name);
            }
            return new ArrayList<>(new TreeSet<>(filtered));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<String> discoverChannels() {
        ChannelRegistry registry = channelRegistryProvider != null
                ? channelRegistryProvider.getIfAvailable() : null;
        if (registry == null) return List.of();
        return new ArrayList<>(new TreeSet<>(registry.channelIds()));
    }

    private static <E extends Enum<E>> List<String> enumNames(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E value : type.getEnumConstants()) names.add(value.name());
        return names;
    }
}
