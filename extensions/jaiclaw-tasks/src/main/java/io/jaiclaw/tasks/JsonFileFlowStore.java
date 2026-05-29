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
 * JSON file-backed flow store.
 */
public class JsonFileFlowStore implements FlowStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileFlowStore.class);

    private final Path storePath;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, TaskFlow> flows = new ConcurrentHashMap<>();

    public JsonFileFlowStore(Path storagePath) {
        this.storePath = storagePath.resolve("flows.json");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadFromDisk();
    }

    @Override
    public void save(TaskFlow flow) {
        flows.put(flow.id(), flow);
        flushToDisk();
    }

    @Override
    public Optional<TaskFlow> findById(String id) {
        return Optional.ofNullable(flows.get(id));
    }

    @Override
    public List<TaskFlow> findAll() {
        return flows.values().stream()
                .sorted(Comparator.comparing(TaskFlow::createdAt).reversed())
                .toList();
    }

    @Override
    public void deleteById(String id) {
        flows.remove(id);
        flushToDisk();
    }

    private void loadFromDisk() {
        if (!Files.exists(storePath)) return;
        try {
            List<TaskFlow> loaded = mapper.readValue(storePath.toFile(),
                    new TypeReference<List<TaskFlow>>() {});
            loaded.forEach(f -> flows.put(f.id(), f));
            log.info("Loaded {} flows from {}", flows.size(), storePath);
        } catch (IOException e) {
            log.warn("Failed to load flows from {}: {}", storePath, e.getMessage());
        }
    }

    private void flushToDisk() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storePath.toFile(), List.copyOf(flows.values()));
        } catch (IOException e) {
            log.error("Failed to flush flows to {}: {}", storePath, e.getMessage());
        }
    }
}
