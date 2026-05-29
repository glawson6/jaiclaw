package io.jaiclaw.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JSON file-backed task store following the DocStore persistence pattern.
 */
public class JsonFileTaskStore implements TaskStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileTaskStore.class);

    private final Path storePath;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    public JsonFileTaskStore(Path storagePath) {
        this.storePath = storagePath.resolve("tasks.json");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadFromDisk();
    }

    @Override
    public void save(TaskRecord task) {
        tasks.put(task.id(), task);
        flushToDisk();
    }

    @Override
    public Optional<TaskRecord> findById(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public List<TaskRecord> findByStatus(TaskStatus status) {
        return tasks.values().stream()
                .filter(t -> t.status() == status)
                .sorted(Comparator.comparing(TaskRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public List<TaskRecord> findAll() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(TaskRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public void deleteById(String id) {
        tasks.remove(id);
        flushToDisk();
    }

    @Override
    public long count() {
        return tasks.size();
    }

    private void loadFromDisk() {
        if (!Files.exists(storePath)) return;
        try {
            List<TaskRecord> loaded = mapper.readValue(storePath.toFile(),
                    new TypeReference<List<TaskRecord>>() {});
            loaded.forEach(t -> tasks.put(t.id(), t));
            log.info("Loaded {} tasks from {}", tasks.size(), storePath);
        } catch (IOException e) {
            log.warn("Failed to load tasks from {}: {}", storePath, e.getMessage());
        }
    }

    private void flushToDisk() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storePath.toFile(), List.copyOf(tasks.values()));
        } catch (IOException e) {
            log.error("Failed to flush tasks to {}: {}", storePath, e.getMessage());
        }
    }
}
