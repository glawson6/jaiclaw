package io.jaiclaw.docstore.repository;

import io.jaiclaw.docstore.model.DocStoreEntry;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * SPI for DocStore persistence. Implementations handle storage of indexed entries.
 */
public interface DocStoreRepository {

    void save(DocStoreEntry entry);

    Optional<DocStoreEntry> findById(String id);

    void deleteById(String id);

    DocStoreEntry update(String id, UnaryOperator<DocStoreEntry> mutator);

    List<DocStoreEntry> findByUserId(String userId, int limit, int offset);

    List<DocStoreEntry> findByChatId(String chatId, int limit, int offset);

    List<DocStoreEntry> findByTags(Set<String> tags, String scopeId);

    List<DocStoreEntry> findByMimeTypePrefix(String mimeTypePrefix, String scopeId);

    List<DocStoreEntry> findRecent(String scopeId, int limit);

    long count(String scopeId);
}
