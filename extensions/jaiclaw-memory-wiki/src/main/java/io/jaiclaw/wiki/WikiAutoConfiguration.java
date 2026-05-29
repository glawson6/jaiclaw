package io.jaiclaw.wiki;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

/**
 * Auto-configuration for the wiki knowledge base. Disabled by default.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
@ConditionalOnProperty(name = "jaiclaw.wiki.enabled", havingValue = "true", matchIfMissing = false)
public class WikiAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WikiAutoConfiguration.class);

    @Bean
    public JsonFileWikiRepository wikiRepository(Environment env,
                                                  ObjectProvider<TenantGuard> tenantGuardProvider) {
        String storageDir = env.getProperty("jaiclaw.wiki.storage-dir",
                System.getProperty("user.home") + "/.jaiclaw/wiki");
        return new JsonFileWikiRepository(Path.of(storageDir), tenantGuardProvider.getIfAvailable());
    }

    @Bean
    public WikiService wikiService(WikiRepository repository) {
        return new WikiService(repository);
    }

    @Bean
    public WikiToolsRegistrar wikiToolsRegistrar(WikiService service, ToolRegistry toolRegistry) {
        WikiTools.registerAll(toolRegistry, service);
        log.info("Wiki tools registered: wiki_read, wiki_write, wiki_search, wiki_list, wiki_delete");
        return new WikiToolsRegistrar();
    }

    public static class WikiToolsRegistrar {}
}
