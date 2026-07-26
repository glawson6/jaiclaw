package io.jaiclaw.pipeline.processors;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.documents.DocumentParser;
import io.jaiclaw.memory.MemorySearchManager;
import io.jaiclaw.pipeline.processors.control.FilterGate;
import io.jaiclaw.pipeline.processors.control.SetMetadata;
import io.jaiclaw.pipeline.processors.data.FileProcessors;
import io.jaiclaw.pipeline.processors.data.HttpFetch;
import io.jaiclaw.pipeline.processors.integration.CamelTemplateLoader;
import io.jaiclaw.pipeline.processors.integration.DocumentParseProcessor;
import io.jaiclaw.pipeline.processors.integration.MemorySearch;
import io.jaiclaw.pipeline.processors.integration.ToolStageProcessor;
import io.jaiclaw.pipeline.processors.preset.PipelinePresetLoader;
import io.jaiclaw.pipeline.processors.transform.FormatProcessors;
import io.jaiclaw.pipeline.processors.transform.JsonProcessors;
import io.jaiclaw.pipeline.processors.transform.RegexProcessors;
import io.jaiclaw.pipeline.processors.transform.TemplateProcessor;
import io.jaiclaw.pipeline.processors.transform.TrimCaseTruncate;
import io.jaiclaw.tools.ToolRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Auto-registers every processor + loader in the Phase 4 pack. Every
 * bean is {@code @ConditionalOnMissingBean} so adopters can swap
 * individual processors with their own implementations. Integration
 * processors that depend on classpath-optional beans (Documents SPI,
 * Memory SPI, Tool registry) are {@code @ConditionalOnBean}-gated so
 * they only register when the runtime bean is present.
 *
 * <p>Opt in via {@code jaiclaw.pipeline.processors.enabled=true}
 * (default {@code true} — matches other pipeline autoconfigs).
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "jaiclaw.pipeline.processors",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class JaiClawPipelineProcessorsAutoConfiguration {

    // ── Transform ────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public TemplateProcessor jaiclawTemplateProcessor() { return new TemplateProcessor(); }

    @Bean @ConditionalOnMissingBean
    public RegexProcessors.Extract jaiclawRegexExtract() { return new RegexProcessors.Extract(); }

    @Bean @ConditionalOnMissingBean
    public RegexProcessors.Replace jaiclawRegexReplace() { return new RegexProcessors.Replace(); }

    @Bean @ConditionalOnMissingBean
    public TrimCaseTruncate jaiclawTrimCaseTruncate() { return new TrimCaseTruncate(); }

    @Bean @ConditionalOnMissingBean
    public JsonProcessors.PathExtract jaiclawJsonPathExtract() { return new JsonProcessors.PathExtract(); }

    @Bean @ConditionalOnMissingBean
    public JsonProcessors.Validate jaiclawJsonValidate() { return new JsonProcessors.Validate(); }

    @Bean @ConditionalOnMissingBean
    public FormatProcessors.JsonCsv jaiclawJsonCsv() { return new FormatProcessors.JsonCsv(); }

    @Bean @ConditionalOnMissingBean
    public FormatProcessors.XmlToJson jaiclawXmlToJson() { return new FormatProcessors.XmlToJson(); }

    @Bean @ConditionalOnMissingBean
    public FormatProcessors.HtmlToText jaiclawHtmlToText() { return new FormatProcessors.HtmlToText(); }

    @Bean @ConditionalOnMissingBean
    public FormatProcessors.MarkdownToHtml jaiclawMarkdownToHtml() { return new FormatProcessors.MarkdownToHtml(); }

    @Bean @ConditionalOnMissingBean
    public FormatProcessors.Chunk jaiclawChunk() { return new FormatProcessors.Chunk(); }

    // ── Control ──────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public FilterGate jaiclawFilterGate() { return new FilterGate(); }

    @Bean @ConditionalOnMissingBean
    public SetMetadata jaiclawSetMetadata() { return new SetMetadata(); }

    // ── Data ─────────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public HttpFetch jaiclawHttpFetch() { return new HttpFetch(); }

    @Bean @ConditionalOnMissingBean(name = "jaiclawFileRead")
    public FileProcessors.Read jaiclawFileRead(ObjectProvider<TenantGuard> tenantGuardProvider,
                                                PipelineProcessorsProperties props) {
        return new FileProcessors.Read(props.fileBaseDir(),
                tenantGuardProvider.getIfAvailable(() -> new TenantGuard(TenantProperties.DEFAULT)));
    }

    @Bean @ConditionalOnMissingBean(name = "jaiclawFileWrite")
    public FileProcessors.Write jaiclawFileWrite(ObjectProvider<TenantGuard> tenantGuardProvider,
                                                  PipelineProcessorsProperties props) {
        return new FileProcessors.Write(props.fileBaseDir(),
                tenantGuardProvider.getIfAvailable(() -> new TenantGuard(TenantProperties.DEFAULT)));
    }

    // ── Integration ─────────────────────────────────

    @Bean
    @ConditionalOnBean(ToolRegistry.class)
    @ConditionalOnMissingBean
    public ToolStageProcessor jaiclawToolStageProcessor(ToolRegistry toolRegistry) {
        return new ToolStageProcessor(toolRegistry);
    }

    @Bean
    @ConditionalOnClass(MemorySearchManager.class)
    @ConditionalOnBean(MemorySearchManager.class)
    @ConditionalOnMissingBean
    public MemorySearch jaiclawMemorySearch(MemorySearchManager searchManager) {
        return new MemorySearch(searchManager);
    }

    @Bean
    @ConditionalOnClass(DocumentParser.class)
    @ConditionalOnBean(DocumentParser.class)
    @ConditionalOnMissingBean
    public DocumentParseProcessor jaiclawDocumentParseProcessor(DocumentParser parser) {
        return new DocumentParseProcessor(parser);
    }

    // ── Preset + Camel-template loaders ─────────────

    @Bean @ConditionalOnMissingBean
    public PipelinePresetLoader jaiclawPipelinePresetLoader() {
        return new PipelinePresetLoader();
    }

    @Bean @ConditionalOnMissingBean
    public CamelTemplateLoader jaiclawCamelTemplateLoader() {
        return new CamelTemplateLoader();
    }

    // ── Properties + defaults ───────────────────────

    @Bean @ConditionalOnMissingBean
    public PipelineProcessorsProperties pipelineProcessorsProperties() {
        return new PipelineProcessorsProperties(defaultFileBaseDir());
    }

    private static Path defaultFileBaseDir() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, ".jaiclaw", "pipeline-files");
    }

    /**
     * Standalone properties record — kept in the autoconfig file for
     * tightness. Bound under {@code jaiclaw.pipeline.processors}.
     */
    public record PipelineProcessorsProperties(Path fileBaseDir) {
        public PipelineProcessorsProperties {
            if (fileBaseDir == null) fileBaseDir = defaultFileBaseDir();
        }
    }
}
