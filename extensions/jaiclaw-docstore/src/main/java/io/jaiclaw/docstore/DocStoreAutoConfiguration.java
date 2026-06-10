package io.jaiclaw.docstore;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.docstore.search.DocStoreSearchProvider;
import io.jaiclaw.docstore.search.FullTextDocStoreSearch;
import io.jaiclaw.docstore.search.HybridDocStoreSearch;
import io.jaiclaw.docstore.search.VectorDocStoreSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for DocStore search providers.
 * <p>
 * When a {@link VectorStore} bean is available, registers a {@link HybridDocStoreSearch}
 * (full-text + vector). Otherwise falls back to {@link FullTextDocStoreSearch}.
 * Both inject {@link TenantGuard} for multi-tenant isolation.
 * <p>
 * Applications can still override by defining their own {@link DocStoreSearchProvider} bean.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
public class DocStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DocStoreAutoConfiguration.class);

    /**
     * Hybrid search (full-text + vector) — activated when VectorStore is available.
     */
    @Configuration
    @ConditionalOnBean(VectorStore.class)
    @ConditionalOnMissingBean(DocStoreSearchProvider.class)
    static class HybridSearchConfiguration {

        @Bean
        DocStoreSearchProvider docStoreSearchProvider(VectorStore vectorStore, TenantGuard tenantGuard) {
            log.info("Configuring HybridDocStoreSearch (full-text + vector) with TenantGuard");
            var fullText = new FullTextDocStoreSearch(tenantGuard);
            var vector = new VectorDocStoreSearch(vectorStore, tenantGuard);
            return new HybridDocStoreSearch(fullText, vector);
        }
    }

    /**
     * Full-text only fallback — activated when no VectorStore is available.
     */
    @Bean
    @ConditionalOnMissingBean(DocStoreSearchProvider.class)
    public DocStoreSearchProvider fullTextDocStoreSearchProvider(TenantGuard tenantGuard) {
        log.info("Configuring FullTextDocStoreSearch (no VectorStore) with TenantGuard");
        return new FullTextDocStoreSearch(tenantGuard);
    }
}
