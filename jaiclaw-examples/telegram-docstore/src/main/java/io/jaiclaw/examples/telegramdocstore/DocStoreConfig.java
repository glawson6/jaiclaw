package io.jaiclaw.examples.telegramdocstore;

import io.jaiclaw.docstore.DocStoreService;
import io.jaiclaw.docstore.analysis.BasicDocStoreAnalyzer;
import io.jaiclaw.docstore.analysis.DocStoreAnalyzer;
import io.jaiclaw.docstore.repository.DocStoreRepository;
import io.jaiclaw.docstore.repository.JsonFileDocStoreRepository;
import io.jaiclaw.docstore.search.DocStoreSearchProvider;
import io.jaiclaw.docstore.search.FullTextDocStoreSearch;
import io.jaiclaw.docstore.telegram.TelegramDocStorePlugin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class DocStoreConfig {

    @Bean
    DocStoreRepository docStoreRepository() {
        return new JsonFileDocStoreRepository(
                Path.of(System.getProperty("user.home"), ".jaiclaw", "docstore"));
    }

    @Bean
    DocStoreSearchProvider docStoreSearchProvider() {
        return new FullTextDocStoreSearch();
    }

    @Bean
    DocStoreAnalyzer docStoreAnalyzer() {
        return new BasicDocStoreAnalyzer();
    }

    @Bean
    DocStoreService docStoreService(DocStoreRepository repository,
                                     DocStoreSearchProvider searchProvider,
                                     DocStoreAnalyzer analyzer) {
        return new DocStoreService(repository, searchProvider, analyzer);
    }

    @Bean
    TelegramDocStorePlugin telegramDocStorePlugin(DocStoreService docStoreService) {
        return new TelegramDocStorePlugin(docStoreService, true, false);
    }
}
