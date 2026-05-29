package io.jaiclaw.tasks;

import java.util.List;
import java.util.Optional;

/**
 * SPI for task flow persistence.
 */
public interface FlowStore {

    void save(TaskFlow flow);

    Optional<TaskFlow> findById(String id);

    List<TaskFlow> findAll();

    void deleteById(String id);
}
