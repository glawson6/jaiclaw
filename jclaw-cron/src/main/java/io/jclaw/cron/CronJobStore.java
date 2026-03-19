package io.jclaw.cron;

import io.jclaw.core.model.CronJob;

import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for cron jobs. Implementations may use
 * JSON files, JDBC, Redis, etc.
 */
public interface CronJobStore {

    void save(CronJob job);

    Optional<CronJob> get(String id);

    List<CronJob> listAll();

    List<CronJob> listEnabled();

    boolean remove(String id);

    int size();
}
