package io.jclaw.cron;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jclaw.core.model.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists cron jobs as a JSON file. Loads on startup, saves on change.
 */
public class JsonFileCronJobStore implements CronJobStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileCronJobStore.class);

    private final Path storePath;
    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public JsonFileCronJobStore(Path storePath) {
        this.storePath = storePath;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        load();
    }

    @Override
    public void save(CronJob job) {
        jobs.put(job.id(), job);
        persist();
    }

    @Override
    public Optional<CronJob> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<CronJob> listAll() {
        return List.copyOf(jobs.values());
    }

    @Override
    public List<CronJob> listEnabled() {
        return jobs.values().stream().filter(CronJob::enabled).toList();
    }

    @Override
    public boolean remove(String id) {
        boolean removed = jobs.remove(id) != null;
        if (removed) persist();
        return removed;
    }

    @Override
    public int size() {
        return jobs.size();
    }

    private void load() {
        if (!Files.exists(storePath)) return;
        try {
            CronJob[] loaded = mapper.readValue(storePath.toFile(), CronJob[].class);
            for (CronJob job : loaded) {
                jobs.put(job.id(), job);
            }
            log.info("Loaded {} cron jobs from {}", jobs.size(), storePath);
        } catch (IOException e) {
            log.warn("Failed to load cron jobs from {}: {}", storePath, e.getMessage());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), jobs.values());
        } catch (IOException e) {
            log.error("Failed to persist cron jobs to {}: {}", storePath, e.getMessage());
        }
    }
}
