package io.jaiclaw.cron;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.core.model.CronJob;
import io.jaiclaw.core.tenant.TenantGuard;
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
    private final TenantGuard tenantGuard;

    public JsonFileCronJobStore(Path storePath) {
        this(storePath, null);
    }

    public JsonFileCronJobStore(Path storePath, TenantGuard tenantGuard) {
        this.storePath = storePath;
        this.tenantGuard = tenantGuard;
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
        CronJob job = jobs.get(id);
        if (job != null && tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(job.tenantId())) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(job);
    }

    @Override
    public List<CronJob> listAll() {
        return tenantFiltered().toList();
    }

    @Override
    public List<CronJob> listEnabled() {
        return tenantFiltered().filter(CronJob::enabled).toList();
    }

    @Override
    public boolean remove(String id) {
        CronJob job = jobs.get(id);
        if (job != null && tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(job.tenantId())) {
                return false;
            }
        }
        boolean removed = jobs.remove(id) != null;
        if (removed) persist();
        return removed;
    }

    private java.util.stream.Stream<CronJob> tenantFiltered() {
        java.util.stream.Stream<CronJob> stream = jobs.values().stream();
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            stream = stream.filter(j -> tenantId.equals(j.tenantId()));
        }
        return stream;
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
