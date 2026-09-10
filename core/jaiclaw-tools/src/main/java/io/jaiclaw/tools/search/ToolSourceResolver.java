package io.jaiclaw.tools.search;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolDefinition;

import java.util.Locale;

/**
 * Works out where a tool came from, for {@code jaiclaw.tools.search.sources}
 * deferral rules.
 *
 * <p>The 1.2.0 plan assumed MCP and Camel tools reached {@code ToolRegistry}
 * through dedicated bridges that could stamp
 * {@link ToolDefinition#source()} at construction. They do not: every tool in
 * this codebase arrives as a Spring {@code ToolCallback} bean via
 * {@code ToolBeanDiscovery}, and MCP integration runs the other way round —
 * JaiClaw <em>hosts</em> MCP tools rather than importing them into the registry.
 *
 * <p>So the source is derived instead of stamped. Precedence:
 * <ol>
 *   <li>an explicit non-default {@code source} on the definition — a tool that
 *       declares its own origin is always believed;</li>
 *   <li>the implementing class's package, which identifies the owning module;</li>
 *   <li>{@link ToolDefinition#SOURCE_BUILTIN} otherwise.</li>
 * </ol>
 *
 * <p>Package inference is a heuristic and is treated as one: it feeds a
 * context-economy control, never an authorization decision. Getting it wrong
 * means a schema is sent that could have been deferred, not that a tool becomes
 * reachable — profile and policy filtering are unaffected.
 *
 * <p>Phase 3 of the 1.2.0 plan, corrected during the closing audit.
 */
@Experimental
public final class ToolSourceResolver {

    private ToolSourceResolver() {}

    /**
     * The effective source for a registered tool.
     *
     * @return one of {@code mcp}, {@code camel}, {@code builtin}, or the owning
     *         extension's short module name (e.g. {@code kanban}, {@code pipeline})
     */
    public static String resolve(ToolCallback tool) {
        if (tool == null) return ToolDefinition.SOURCE_BUILTIN;
        ToolDefinition def = tool.definition();

        // A tool that states its own origin is always believed.
        if (def != null && def.source() != null
                && !ToolDefinition.SOURCE_BUILTIN.equals(def.source())) {
            return def.source();
        }
        return fromPackage(tool.getClass().getName());
    }

    /**
     * Derives a source from a fully-qualified class name.
     *
     * <p>Visible for testing, and deliberately total — an unrecognised package
     * yields {@code builtin} rather than throwing, because a misfiled tool must
     * still register.
     */
    static String fromPackage(String className) {
        if (className == null || className.isBlank()) return ToolDefinition.SOURCE_BUILTIN;
        String fqn = className.toLowerCase(Locale.ROOT);

        // Ordered most- to least-specific: "mcp" appears inside several module
        // packages, so it must be checked before the generic module extraction.
        if (fqn.contains(".mcp.")) return ToolDefinition.SOURCE_MCP;
        if (fqn.contains(".camel.") || fqn.contains("camelroute")) return ToolDefinition.SOURCE_CAMEL;

        // io.jaiclaw.<module>....  → the module segment names the source.
        final String prefix = "io.jaiclaw.";
        if (fqn.startsWith(prefix)) {
            String rest = fqn.substring(prefix.length());
            int dot = rest.indexOf('.');
            String module = dot < 0 ? rest : rest.substring(0, dot);
            // "tools" is where the built-ins live; report them as builtin rather
            // than as a module, so `sources: [builtin]` means what an operator expects.
            if (module.isBlank() || module.equals("tools") || module.equals("core")) {
                return ToolDefinition.SOURCE_BUILTIN;
            }
            return module;
        }
        return ToolDefinition.SOURCE_BUILTIN;
    }
}
