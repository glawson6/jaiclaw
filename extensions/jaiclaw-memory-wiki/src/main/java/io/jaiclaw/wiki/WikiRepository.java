package io.jaiclaw.wiki;

import java.util.List;
import java.util.Optional;

/**
 * SPI for wiki page persistence.
 */
public interface WikiRepository {

    void save(WikiPage page);

    Optional<WikiPage> findById(String id);

    Optional<WikiPage> findByTitle(String title);

    List<WikiPage> findByCategory(String category);

    List<WikiPage> findByTag(String tag);

    List<WikiPage> findAll();

    void deleteById(String id);

    long count();
}
