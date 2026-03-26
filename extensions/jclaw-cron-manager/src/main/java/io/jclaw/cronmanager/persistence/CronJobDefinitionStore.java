package io.jclaw.cronmanager.persistence;

import io.jclaw.cronmanager.model.CronJobDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for cron job definitions (extended metadata).
 * Implementations may use H2, MySQL, Redis, etc.
 */
public interface CronJobDefinitionStore {

    void save(CronJobDefinition definition);

    Optional<CronJobDefinition> findById(String id);

    List<CronJobDefinition> findAll();

    List<CronJobDefinition> findEnabled();

    boolean deleteById(String id);

    void updateEnabled(String id, boolean enabled);
}
