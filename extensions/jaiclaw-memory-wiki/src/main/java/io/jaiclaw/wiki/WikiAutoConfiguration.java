package io.jaiclaw.wiki;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.docstore.repository.DocStoreRepository;
import io.jaiclaw.docstore.repository.JsonFileDocStoreRepository;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

/**
 * Auto-configuration for the wiki knowledge base. Disabled by default.
 * Delegates persistence to DocStore. If a DocStoreRepository bean exists,
 * it is reused; otherwise a dedicated JsonFileDocStoreRepository is created.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
@ConditionalOnProperty(name = "jaiclaw.wiki.enabled", havingValue = "true", matchIfMissing = false)
public class WikiAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WikiAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(WikiRepository.class)
    public DocStoreWikiRepository wikiRepository(ObjectProvider<DocStoreRepository> docStoreProvider,
                                                  Environment env,
                                                  ObjectProvider<TenantGuard> tenantGuardProvider) {
        DocStoreRepository docStore = docStoreProvider.getIfAvailable();
        if (docStore == null) {
            String storageDir = env.getProperty("jaiclaw.wiki.storage-dir",
                    System.getProperty("user.home") + "/.jaiclaw/wiki");
            docStore = new JsonFileDocStoreRepository(Path.of(storageDir),
                    tenantGuardProvider.getIfAvailable());
            log.info("Wiki using dedicated JsonFileDocStoreRepository at {}", storageDir);
        } else {
            log.info("Wiki using shared DocStoreRepository bean");
        }
        return new DocStoreWikiRepository(docStore);
    }

    @Bean
    public WikiService wikiService(WikiRepository repository) {
        return new WikiService(repository);
    }

    // Wiki tools as Spring beans. ToolBeanDiscovery picks them up
    // automatically.

    @Bean
    public ToolCallback wikiReadTool(WikiService service) {
        return new WikiReadTool(service);
    }

    @Bean
    public ToolCallback wikiWriteTool(WikiService service) {
        return new WikiWriteTool(service);
    }

    @Bean
    public ToolCallback wikiSearchTool(WikiService service) {
        return new WikiSearchTool(service);
    }

    @Bean
    public ToolCallback wikiListTool(WikiService service) {
        return new WikiListTool(service);
    }

    @Bean
    public ToolCallback wikiDeleteTool(WikiService service) {
        return new WikiDeleteTool(service);
    }
}
