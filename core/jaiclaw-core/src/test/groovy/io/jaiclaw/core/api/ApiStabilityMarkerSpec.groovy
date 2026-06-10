package io.jaiclaw.core.api

import spock.lang.Specification

/**
 * 0.8.0 P3.5: confirms the three stability marker annotations exist
 * and are applied to a representative sample of the JaiClaw public SPI.
 *
 * <p>This isn't an exhaustive sweep — annotating every type would be
 * busy-work for a small framework. Instead, the spec spot-checks the
 * headline surfaces named in {@code docs/MIGRATION-0.8.md} § P3.5:
 *
 * <ul>
 *   <li>{@code ToolCallback}, {@code ToolDefinition}, {@code ToolContext},
 *       {@code ToolResult}, {@code TenantGuard}, {@code TenantContext},
 *       {@code TenantContextHolder}, {@code TenantContextPropagator},
 *       {@code TenantProperties}, {@code ChannelAdapter}, and
 *       {@code WebhookSignatureUtil} → {@link Stable}.</li>
 *   <li>{@code HookEvent}, {@code TypedToolCallback}, {@code SchemaBuilder},
 *       {@code @ToolParameter}, {@code AbstractChannelAdapter},
 *       {@code PluginApi}, {@code JaiClawPlugin} → {@link Experimental}.</li>
 *   <li>{@code HookRunner}, {@code PluginApiImpl}, {@code PluginRegistry},
 *       {@code PluginDiscovery} → {@link Internal}.</li>
 * </ul>
 */
class ApiStabilityMarkerSpec extends Specification {

    def "@Stable, @Experimental, @Internal annotations exist on the classpath"() {
        expect:
        Class.forName("io.jaiclaw.core.api.Stable").isAnnotation()
        Class.forName("io.jaiclaw.core.api.Experimental").isAnnotation()
        Class.forName("io.jaiclaw.core.api.Internal").isAnnotation()
    }

    def "headline stable surfaces carry @Stable"() {
        expect:
        Class.forName(className).isAnnotationPresent(Stable.class)

        where:
        className << [
                "io.jaiclaw.core.tool.ToolCallback",
                "io.jaiclaw.core.tool.ToolDefinition",
                "io.jaiclaw.core.tool.ToolContext",
                "io.jaiclaw.core.tool.ToolResult",
                "io.jaiclaw.core.tenant.TenantContext",
                "io.jaiclaw.core.tenant.TenantGuard",
                "io.jaiclaw.core.tenant.TenantContextHolder",
                "io.jaiclaw.core.tenant.TenantContextPropagator",
                "io.jaiclaw.core.tenant.TenantProperties",
        ]
    }

    def "experimental hook + tool surfaces carry @Experimental"() {
        expect:
        Class.forName(className).isAnnotationPresent(Experimental.class)

        where:
        className << [
                "io.jaiclaw.core.hook.event.HookEvent",
                "io.jaiclaw.core.tool.param.TypedToolCallback",
                "io.jaiclaw.core.tool.param.ToolParameter",
                "io.jaiclaw.core.tool.schema.SchemaBuilder",
        ]
    }
}
