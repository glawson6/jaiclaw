package io.jaiclaw.kanban.persistence;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLGenerator;
import io.jaiclaw.kanban.loader.BoardYamlParser;
import io.jaiclaw.kanban.model.BoardDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One YAML file per board: {@code {boards-dir}/{boardId}.yaml}. Reads
 * happen on construction (full directory scan); writes go through
 * {@code tmp + ATOMIC_MOVE}.
 *
 * <p>Mirrors the durability guarantees of {@code JsonFileTaskStore} —
 * crash during write cannot leave a truncated file. Hand-edits and REST
 * writes both land in the same directory, so {@code git diff} stays
 * honest.
 */
public class YamlFileBoardStore implements BoardStore {

    private static final Logger log = LoggerFactory.getLogger(YamlFileBoardStore.class);

    private static final String SUFFIX = ".yaml";

    private final Path boardsDir;
    private final ObjectMapper yaml;

    public YamlFileBoardStore(Path boardsDir) {
        this.boardsDir = boardsDir;
        this.yaml = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
                
                ;
        try {
            Files.createDirectories(boardsDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create kanban boards dir: " + boardsDir, e);
        }
    }

    @Override
    public synchronized void save(BoardDefinition board) {
        Path target = boardsDir.resolve(board.id() + SUFFIX);
        Path tmp = boardsDir.resolve(board.id() + SUFFIX + ".tmp");
        try {
            yaml.writeValue(tmp.toFile(), board);
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Persisted board {} to {}", board.id(), target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist board " + board.id() + " to " + target, e);
        }
    }

    @Override
    public synchronized boolean delete(String boardId) {
        if (boardId == null) return false;
        Path target = boardsDir.resolve(boardId + SUFFIX);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete board file {}: {}", target, e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<BoardDefinition> findById(String boardId) {
        if (boardId == null) return Optional.empty();
        Path target = boardsDir.resolve(boardId + SUFFIX);
        if (!Files.exists(target)) return Optional.empty();
        return tryParse(target);
    }

    @Override
    public List<BoardDefinition> findAll() {
        if (!Files.exists(boardsDir)) return List.of();
        List<BoardDefinition> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(boardsDir)) {
            stream
                    .filter(p -> p.getFileName().toString().endsWith(SUFFIX))
                    .filter(p -> !p.getFileName().toString().endsWith(SUFFIX + ".tmp"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> tryParse(p).ifPresent(out::add));
        } catch (IOException e) {
            log.warn("Failed to list boards dir {}: {}", boardsDir, e.getMessage());
        }
        return out;
    }

    @Override
    public long count() {
        return findAll().size();
    }

    private Optional<BoardDefinition> tryParse(Path file) {
        String fallbackId = stem(file.getFileName().toString());
        try (InputStream in = Files.newInputStream(file)) {
            return Optional.of(BoardYamlParser.parse(in, fallbackId, file.toString()));
        } catch (Exception e) {
            log.warn("Skipping malformed board file {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
