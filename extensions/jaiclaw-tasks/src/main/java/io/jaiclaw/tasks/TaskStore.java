package io.jaiclaw.tasks;

import java.util.List;
import java.util.Optional;

/**
 * SPI for task persistence.
 */
public interface TaskStore {

    void save(TaskRecord task);

    Optional<TaskRecord> findById(String id);

    List<TaskRecord> findByStatus(TaskStatus status);

    List<TaskRecord> findAll();

    void deleteById(String id);

    long count();
}
