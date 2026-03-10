package io.jclaw.memory;

import java.util.List;

/**
 * Interface for semantic search over workspace files and conversation history.
 */
public interface MemorySearchManager {

    List<MemorySearchResult> search(String query, MemorySearchOptions options);

    default List<MemorySearchResult> search(String query) {
        return search(query, MemorySearchOptions.DEFAULT);
    }
}
