package io.jaiclaw.autoconfigure;

import io.jaiclaw.config.CompositeToolProfileRegistry;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.config.ToolsProperties;
import io.jaiclaw.core.tool.CompositeToolProfile;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.builtin.BuiltinTools;
import io.jaiclaw.tools.builtin.ImageGenerationTool;
import io.jaiclaw.tools.builtin.WebSearchTool;
import io.jaiclaw.tools.builtin.ascii.AsciiRenderProfilesInitializer;
import io.jaiclaw.tools.builtin.ascii.AsciiRenderProperties;
import io.jaiclaw.tools.discovery.ToolBeanDiscovery;
import io.jaiclaw.tools.exec.ExecPolicyConfig;
import io.jaiclaw.tools.exec.KubectlPolicyConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tools, exec policies, image generation, voice (TTS/STT) and video providers.
 *
 * <p>Beans defined here:
 * <ul>
 *   <li>{@link ToolRegistry} + built-in tools.</li>
 *   <li>{@link CompositeToolProfileRegistry} — resolves composite tool
 *       profiles from {@code jaiclaw.tools.composite-profiles.*}.</li>
 *   <li>{@link KubectlPolicyConfig}.</li>
 *   <li>{@link ImageGenerationTool} — gated on a Spring AI {@code ImageModel} bean.</li>
 *   <li>Embabel {@link io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort}
 *       no-op fallback.</li>
 *   <li>Nested voice (ElevenLabs TTS, Deepgram STT) and video
 *       (Runway, registry, tool) auto-configs.</li>
 * </ul>
 *
 * <p>Runs after {@link JaiClawTenantAutoConfiguration} so tools that need
 * tenant-aware initialisation can pick up the {@code TenantGuard} bean from
 * downstream consumers.
 *
 * <p>Carved out of the former {@code JaiClawAutoConfiguration} monolith
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.4, Phase 3 P3.4).
 */
@AutoConfiguration(after = JaiClawTenantAutoConfiguration.class)
@EnableConfigurationProperties({JaiClawProperties.class, AsciiRenderProperties.class})
public class JaiClawToolsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawToolsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(JaiClawProperties properties) {
        var registry = new ToolRegistry();
        ExecPolicyConfig execPolicyConfig = toExecPolicyConfig(properties.tools().exec());
        boolean ssrfProtection = properties.tools().web().ssrfProtection();
        // Seed all built-ins except web_search; web_search is supplied via a
        // separate @ConditionalOnMissingBean(name = "webSearchTool") @Bean so
        // extensions like jaiclaw-web-search can override it without colliding
        // inside ToolRegistry. See ToolBeanDiscovery for the new fail-fast
        // collision behaviour any other tool name now obeys.
        registry.registerAll(BuiltinTools.allExceptWebSearch(execPolicyConfig, ssrfProtection));
        return registry;
    }

    /**
     * Default {@code web_search} tool. Suppressed when another {@link ToolCallback}
     * bean named {@code webSearchTool} is present — e.g. the
     * {@code RegistryWebSearchTool} contributed by {@code jaiclaw-web-search}.
     * Registration into {@link ToolRegistry} happens via {@link ToolBeanDiscovery}.
     */
    @Bean(name = "webSearchTool")
    @ConditionalOnMissingBean(name = "webSearchTool")
    public ToolCallback webSearchTool() {
        return new WebSearchTool();
    }

    /**
     * Wire {@code jaiclaw.ascii.*} configuration into the static
     * {@link io.jaiclaw.asciirender.profile.AsciiRenderProfiles} registry.
     * Constructed eagerly so operator-supplied profile overrides + the
     * default-profile setting are in place before any
     * {@code ascii_box} / {@code ascii_render} tool call resolves a profile.
     */
    @Bean
    @ConditionalOnMissingBean
    public AsciiRenderProfilesInitializer asciiRenderProfilesInitializer(
            AsciiRenderProperties properties) {
        return new AsciiRenderProfilesInitializer(properties);
    }

    /**
     * Auto-discovery for any Spring-managed {@link ToolCallback} bean. Mirrors
     * the {@code PluginDiscovery} / {@code ChannelRegistry} pattern. Runs after
     * all {@code @Bean} factories — including the {@code webSearchTool} above —
     * have produced their tools, so every bean lands in {@link ToolRegistry}
     * via a single funnel with fail-fast collision detection.
     */
    @Bean
    public ToolBeanDiscovery toolBeanDiscovery(
            ToolRegistry toolRegistry,
            ObjectProvider<List<ToolCallback>> toolBeansProvider) {
        ToolBeanDiscovery discovery = new ToolBeanDiscovery(toolRegistry);
        List<ToolCallback> beans = toolBeansProvider.getIfAvailable();
        if (beans != null && !beans.isEmpty()) {
            discovery.discoverAndRegister(beans);
        }
        return discovery;
    }

    @Bean
    @ConditionalOnMissingBean
    public CompositeToolProfileRegistry compositeToolProfileRegistry(JaiClawProperties properties) {
        CompositeToolProfileRegistry registry = new CompositeToolProfileRegistry();
        Map<String, ToolsProperties.CompositeProfileEntry> entries = properties.tools().compositeProfiles();
        for (Map.Entry<String, ToolsProperties.CompositeProfileEntry> entry : entries.entrySet()) {
            String name = entry.getKey();
            ToolsProperties.CompositeProfileEntry cfg = entry.getValue();

            Set<ToolProfile> baseProfiles = new LinkedHashSet<>();
            for (String profileName : cfg.profiles()) {
                try {
                    baseProfiles.add(ToolProfile.valueOf(profileName.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown base profile '{}' in composite profile '{}' — skipping", profileName, name);
                }
            }

            if (baseProfiles.isEmpty()) {
                log.warn("Composite profile '{}' has no valid base profiles — skipping", name);
                continue;
            }

            CompositeToolProfile composite = new CompositeToolProfile(name, baseProfiles, cfg.allow(), cfg.deny());
            registry.register(composite);
            log.info("Registered composite tool profile '{}': profiles={}, allow={}, deny={}",
                    name, baseProfiles, cfg.allow(), cfg.deny());
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public KubectlPolicyConfig kubectlPolicyConfig(JaiClawProperties properties) {
        return toKubectlPolicyConfig(properties.tools().exec().kubectl());
    }

    @Bean
    @ConditionalOnBean(ImageModel.class)
    @ConditionalOnMissingBean(ImageGenerationTool.class)
    public ImageGenerationTool imageGenerationTool(ImageModel imageModel, ToolRegistry toolRegistry) {
        var tool = new ImageGenerationTool(imageModel);
        toolRegistry.register(tool);
        log.info("ImageGenerationTool registered (generate_image)");
        return tool;
    }

    /**
     * Orchestration port auto-configuration — no-op fallback when no real
     * Embabel orchestrator is on the classpath.
     */
    @Bean
    @ConditionalOnMissingBean(type = "io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort")
    public io.jaiclaw.tools.bridge.embabel.NoOpOrchestrationPort noOpOrchestrationPort() {
        return new io.jaiclaw.tools.bridge.embabel.NoOpOrchestrationPort();
    }

    private static ExecPolicyConfig toExecPolicyConfig(ToolsProperties.ExecToolProperties props) {
        return new ExecPolicyConfig(
                props.policy(),
                props.allowedCommands(),
                props.blockedPatterns(),
                props.maxTimeout()
        );
    }

    private static KubectlPolicyConfig toKubectlPolicyConfig(ToolsProperties.KubectlPolicyProperties props) {
        return new KubectlPolicyConfig(
                props.policy(),
                props.allowedVerbs(),
                props.blockedVerbs()
        );
    }

    // --- Voice provider auto-configuration ---

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.voice.tts.ElevenLabsTtsProvider")
    static class ElevenLabsTtsAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "elevenLabsTtsProvider")
        @ConditionalOnProperty(prefix = "jaiclaw.voice", name = "elevenlabs-api-key")
        public io.jaiclaw.voice.tts.ElevenLabsTtsProvider elevenLabsTtsProvider(JaiClawProperties properties) {
            io.jaiclaw.config.VoiceProperties voice = properties.voice();
            return new io.jaiclaw.voice.tts.ElevenLabsTtsProvider(
                    voice.elevenLabsApiKey(), voice.elevenLabsVoiceId(), voice.elevenLabsModelId());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.voice.stt.DeepgramSttProvider")
    static class DeepgramSttAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "deepgramSttProvider")
        @ConditionalOnProperty(prefix = "jaiclaw.voice", name = "deepgram-api-key")
        public io.jaiclaw.voice.stt.DeepgramSttProvider deepgramSttProvider(JaiClawProperties properties) {
            io.jaiclaw.config.VoiceProperties voice = properties.voice();
            return new io.jaiclaw.voice.stt.DeepgramSttProvider(
                    voice.deepgramApiKey(), voice.deepgramModel());
        }
    }

    // --- Video generation auto-configuration ---

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.video.VideoGenerationRegistry")
    static class VideoGenerationAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "jaiclaw.video", name = "runway-api-key")
        public io.jaiclaw.video.RunwayVideoProvider runwayVideoProvider(JaiClawProperties properties) {
            io.jaiclaw.config.VideoProperties video = properties.video();
            return new io.jaiclaw.video.RunwayVideoProvider(video.runwayApiKey(), video.runwayModel());
        }

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.video.VideoGenerationRegistry videoGenerationRegistry(
                org.springframework.beans.factory.ObjectProvider<List<io.jaiclaw.video.VideoGenerationProvider>> providersProvider) {
            List<io.jaiclaw.video.VideoGenerationProvider> providers = providersProvider.getIfAvailable();
            return new io.jaiclaw.video.VideoGenerationRegistry(
                    providers != null ? providers : List.of());
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jaiclaw.video.VideoGenerationRegistry.class)
        public io.jaiclaw.video.VideoGenerationTool videoGenerationTool(
                io.jaiclaw.video.VideoGenerationRegistry registry,
                ToolRegistry toolRegistry,
                JaiClawProperties properties) {
            String defaultProvider = properties.video().defaultProvider();
            io.jaiclaw.video.VideoGenerationTool tool = new io.jaiclaw.video.VideoGenerationTool(registry, defaultProvider);
            toolRegistry.register(tool);
            log.info("VideoGenerationTool registered (video_generate)");
            return tool;
        }
    }

    // ─── Tool Search / deferred schemas (1.2.0 Phase 3) ──────────────────────
    // Off by default: with jaiclaw.tools.search.enabled=false nothing is deferred
    // and tool_search is not registered, so the model sees the pre-1.2.0 list.

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.config.ToolSearchProperties toolSearchProperties(
            org.springframework.core.env.Environment env) {
        return org.springframework.boot.context.properties.bind.Binder.get(env)
                .bind("jaiclaw.tools.search", io.jaiclaw.config.ToolSearchProperties.class)
                .orElseGet(io.jaiclaw.config.ToolSearchProperties::defaults);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "jaiclaw.tools.search", name = "enabled", havingValue = "true")
    public io.jaiclaw.tools.search.SessionToolDiscoveries sessionToolDiscoveries() {
        return new io.jaiclaw.tools.search.SessionToolDiscoveries();
    }

    /**
     * Registers {@code tool_search} and applies the configured deferral rules.
     *
     * <p>Deferral is applied here, after {@link ToolBeanDiscovery} has registered
     * every tool bean, so rules can match tools contributed by plugins and MCP
     * bridges rather than only the built-ins present at registry construction.
     */
    @Bean
    @ConditionalOnBean(io.jaiclaw.tools.search.SessionToolDiscoveries.class)
    @ConditionalOnMissingBean(io.jaiclaw.tools.builtin.ToolSearchTool.class)
    public io.jaiclaw.tools.builtin.ToolSearchTool toolSearchTool(
            ToolRegistry toolRegistry,
            io.jaiclaw.tools.search.SessionToolDiscoveries discoveries,
            io.jaiclaw.config.ToolSearchProperties searchProperties) {
        var tool = new io.jaiclaw.tools.builtin.ToolSearchTool(
                toolRegistry, discoveries, searchProperties.limit());
        toolRegistry.register(tool);

        if (searchProperties.hasDeferralRules()) {
            // The BiPredicate overload supplies the RESOLVED source (derived from
            // the tool's package when it does not stamp one itself). Using the
            // single-argument form here would silently match almost nothing for
            // `sources:` rules.
            int deferredCount = toolRegistry.markDeferred((def, source) ->
                    // tool_search itself must never be deferred — the model would
                    // have no way to discover the thing that does discovery.
                    !"tool_search".equals(def.name())
                            && searchProperties.matches(def.name(), def.section(), source));
            log.info("Tool search ENABLED — {} of {} tools deferred; tool_search registered",
                    deferredCount, toolRegistry.size());
        } else {
            log.info("Tool search ENABLED — no deferral rules configured; "
                    + "tool_search registered but all schemas are still sent");
        }
        return tool;
    }
}
