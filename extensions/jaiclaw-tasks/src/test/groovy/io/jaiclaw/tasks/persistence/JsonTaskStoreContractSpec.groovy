package io.jaiclaw.tasks.persistence

import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskStore
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Pins {@link JsonFileTaskStore} against the shared
 * {@link TaskStoreContractSpec}. Same Phase 1 store, asserted to honour
 * the same SPI invariants every other backend must.
 */
class JsonTaskStoreContractSpec extends TaskStoreContractSpec {

    @TempDir
    Path tempDir

    @Override
    TaskStore createStore() {
        // Each test method gets its own subdirectory so persisted state
        // doesn't leak between methods (TempDir is per-spec by default in
        // Spock's Spec scope, not per-method).
        Path dir = Files.createTempDirectory(tempDir, "json-store")
        return new JsonFileTaskStore(dir)
    }
}
