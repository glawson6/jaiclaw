package io.jaiclaw.tools.discovery;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-registers {@link ToolCallback} beans into a {@link ToolRegistry}.
 *
 * <p>Mirrors the Spring-bean discovery pattern already used by
 * {@code PluginDiscovery}, {@code ChannelRegistry}, and
 * {@code McpServerRegistry}: a Spring auto-configuration injects
 * {@code ObjectProvider<List<ToolCallback>>}, hands the list to this
 * class, and the registry is populated automatically.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>For each bean, looks up {@link ToolCallback#definition()}'s
 *       {@code name()}.</li>
 *   <li>If the {@link ToolRegistry} already contains a tool with that
 *       name (e.g. registered by a built-in seed or by a legacy
 *       {@code toolRegistry.registerAll(...)} call in an extension
 *       module), the discovery <b>fails fast</b> with an
 *       {@link IllegalStateException} naming both conflicting tool
 *       classes. There is no last-wins; intentional overrides must use
 *       Spring's {@code @ConditionalOnMissingBean} pattern.</li>
 *   <li>If two discovered beans share the same name, the second one
 *       triggers the same fail-fast at registration time.</li>
 *   <li>Null / empty / {@code null}-element input is a no-op.</li>
 * </ul>
 *
 * <p>This class has no Spring dependency — it is plain Java and can be
 * exercised directly from unit tests.
 */
public final class ToolBeanDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ToolBeanDiscovery.class);

    private final ToolRegistry registry;

    public ToolBeanDiscovery(ToolRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.registry = registry;
    }

    /**
     * Register each {@code ToolCallback} bean into the registry.
     *
     * @param beans list of Spring-managed tool beans (may be null or empty)
     * @return number of beans actually registered
     * @throws IllegalStateException if a bean's tool name collides with a
     *         tool already present in the registry, or with another bean
     *         in the same input list
     */
    public int discoverAndRegister(List<ToolCallback> beans) {
        if (beans == null || beans.isEmpty()) {
            return 0;
        }

        Map<String, ToolCallback> seenInThisRun = new HashMap<>();
        List<String> registered = new ArrayList<>();

        for (ToolCallback bean : beans) {
            if (bean == null) {
                continue;
            }
            String name = bean.definition() == null ? null : bean.definition().name();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(
                        "Tool bean " + bean.getClass().getName()
                                + " has a null or blank definition().name() — cannot auto-register.");
            }

            ToolCallback duplicateInBatch = seenInThisRun.get(name);
            if (duplicateInBatch != null) {
                throw new IllegalStateException(
                        "Duplicate tool name '" + name + "' across Spring beans: "
                                + duplicateInBatch.getClass().getName()
                                + " AND " + bean.getClass().getName()
                                + ". Fix: rename one of the tools, or remove the duplicate bean."
                );
            }

            if (registry.contains(name)) {
                ToolCallback existing = registry.resolve(name).orElse(null);
                String existingClass = existing == null ? "<unknown>" : existing.getClass().getName();
                throw new IllegalStateException(
                        "Tool name collision: '" + name + "' is already registered by "
                                + existingClass + " but a new Spring bean "
                                + bean.getClass().getName() + " also wants to register it. "
                                + "Fix: rename one of the tools, or move the override to a "
                                + "@ConditionalOnMissingBean(name=\"" + name + "\") pattern so only "
                                + "one tool of this name is present at registration time."
                );
            }

            registry.register(bean);
            seenInThisRun.put(name, bean);
            registered.add(name);
        }

        if (!registered.isEmpty()) {
            log.info("ToolBeanDiscovery registered {} Spring-discovered tool bean(s): {} — "
                            + "ToolRegistry now holds {} tool(s): {}",
                    registered.size(), registered, registry.size(), registry.toolNames());
        }
        return registered.size();
    }
}
