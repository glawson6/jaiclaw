package io.jaiclaw.subscription.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.subscription.Subscription;
import io.jaiclaw.subscription.SubscriptionRepository;
import io.jaiclaw.subscription.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON file-backed subscription repository. Loads from disk on startup,
 * flushes to disk on every write. Follows the jaiclaw-identity JSON persistence pattern.
 */
public class JsonFileSubscriptionRepository implements SubscriptionRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonFileSubscriptionRepository.class);

    private final Path storePath;
    private final ObjectMapper mapper;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public JsonFileSubscriptionRepository(Path storagePath) {
        this.storePath = storagePath.resolve("subscriptions.json");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadFromDisk();
    }

    @Override
    public Optional<Subscription> findById(String id) {
        return Optional.ofNullable(subscriptions.get(id));
    }

    @Override
    public List<Subscription> findByUserId(String userId) {
        return subscriptions.values().stream()
                .filter(s -> userId.equals(s.userId()))
                .toList();
    }

    @Override
    public List<Subscription> findExpired(Instant before) {
        return subscriptions.values().stream()
                .filter(s -> s.status() == SubscriptionStatus.ACTIVE || s.status() == SubscriptionStatus.PAST_DUE)
                .filter(s -> s.expiresAt() != null && s.expiresAt().isBefore(before))
                .toList();
    }

    @Override
    public Subscription save(Subscription subscription) {
        subscriptions.put(subscription.id(), subscription);
        flushToDisk();
        return subscription;
    }

    @Override
    public void deleteById(String id) {
        subscriptions.remove(id);
        flushToDisk();
    }

    private void loadFromDisk() {
        if (!Files.exists(storePath)) return;
        try {
            List<Subscription> loaded = mapper.readValue(storePath.toFile(),
                    new TypeReference<List<Subscription>>() {});
            loaded.forEach(s -> subscriptions.put(s.id(), s));
            log.info("Loaded {} subscriptions from {}", subscriptions.size(), storePath);
        } catch (IOException e) {
            log.warn("Failed to load subscriptions from {}: {}", storePath, e.getMessage());
        }
    }

    private void flushToDisk() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storePath.toFile(), List.copyOf(subscriptions.values()));
        } catch (IOException e) {
            log.error("Failed to flush subscriptions to {}: {}", storePath, e.getMessage());
        }
    }
}
