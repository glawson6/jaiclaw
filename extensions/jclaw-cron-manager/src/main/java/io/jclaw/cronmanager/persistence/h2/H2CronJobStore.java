package io.jclaw.cronmanager.persistence.h2;

import io.jclaw.core.model.CronJob;
import io.jclaw.cron.CronJobStore;
import io.jclaw.cronmanager.model.CronJobDefinition;
import io.jclaw.cronmanager.persistence.CronJobDefinitionStore;

import java.util.List;
import java.util.Optional;

/**
 * Bridges the existing {@link CronJobStore} interface (used by {@link io.jclaw.cron.CronService})
 * to the H2-backed {@link CronJobDefinitionStore}. Extracts {@link CronJob} from
 * {@link CronJobDefinition} for compatibility with the scheduler.
 */
public class H2CronJobStore implements CronJobStore {

    private final CronJobDefinitionStore definitionStore;

    public H2CronJobStore(CronJobDefinitionStore definitionStore) {
        this.definitionStore = definitionStore;
    }

    @Override
    public void save(CronJob job) {
        Optional<CronJobDefinition> existing = definitionStore.findById(job.id());
        if (existing.isPresent()) {
            // Preserve extended metadata, update only the CronJob portion
            definitionStore.save(existing.get().withCronJob(job));
        } else {
            definitionStore.save(new CronJobDefinition(job));
        }
    }

    @Override
    public Optional<CronJob> get(String id) {
        return definitionStore.findById(id).map(CronJobDefinition::cronJob);
    }

    @Override
    public List<CronJob> listAll() {
        return definitionStore.findAll().stream()
                .map(CronJobDefinition::cronJob)
                .toList();
    }

    @Override
    public List<CronJob> listEnabled() {
        return definitionStore.findEnabled().stream()
                .map(CronJobDefinition::cronJob)
                .toList();
    }

    @Override
    public boolean remove(String id) {
        return definitionStore.deleteById(id);
    }

    @Override
    public int size() {
        return definitionStore.findAll().size();
    }
}
