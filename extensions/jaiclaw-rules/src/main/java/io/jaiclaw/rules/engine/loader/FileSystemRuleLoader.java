package io.jaiclaw.rules.engine.loader;

import org.kie.api.builder.KieFileSystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule loader implementation that loads DRL files from the filesystem.
 * Supports glob patterns for file matching and includes security validation
 * to prevent directory traversal attacks.
 */
public class FileSystemRuleLoader extends AbstractRuleLoader {

    private static final String LOADER_TYPE = "filesystem";
    private static final int MAX_DEPTH = 20;

    public FileSystemRuleLoader(List<String> locations, boolean enabled, int priority) {
        super(locations, enabled, priority);
    }

    public FileSystemRuleLoader(List<String> locations, boolean enabled) {
        this(locations, enabled, 100);
    }

    @Override
    public String getLoaderType() {
        return LOADER_TYPE;
    }

    @Override
    public void loadRules(KieFileSystem kieFileSystem) throws IOException, RuleLoadingException {
        if (!enabled) {
            logger.debug("Filesystem rule loader is disabled, skipping");
            return;
        }

        logger.info("Loading rules from filesystem locations: {}", locations);

        int totalRulesLoaded = 0;
        List<String> failedLocations = new ArrayList<>();
        List<String> emptyLocations = new ArrayList<>();

        for (String location : locations) {
            try {
                int rulesLoaded = loadRulesFromLocation(kieFileSystem, location);
                if (rulesLoaded == 0) {
                    emptyLocations.add(location);
                    logger.warn("No rules found at filesystem location: {}", location);
                }
                totalRulesLoaded += rulesLoaded;
            } catch (RuleLoadingException e) {
                failedLocations.add(location);
                logger.warn("Failed to load rules from filesystem location: {}", location, e);
            }
        }

        if (totalRulesLoaded == 0) {
            List<String> allProblematicLocations = new ArrayList<>();
            allProblematicLocations.addAll(failedLocations);
            allProblematicLocations.addAll(emptyLocations);

            throw new RuleLoadingException(
                LOADER_TYPE,
                String.join(", ", allProblematicLocations),
                "No rules could be loaded from any configured filesystem location"
            );
        }

        logger.info("Successfully loaded {} rule file(s) from filesystem", totalRulesLoaded);
    }

    private int loadRulesFromLocation(KieFileSystem kieFileSystem, String location)
            throws RuleLoadingException {

        String sanitizedLocation = sanitizePath(location);
        Path basePath = resolveBasePath(sanitizedLocation);

        if (!Files.exists(basePath)) {
            logger.warn("Filesystem location does not exist: {}", basePath);
            return 0;
        }

        List<Path> ruleFiles;
        try {
            if (Files.isDirectory(basePath)) {
                ruleFiles = findRuleFilesInDirectory(basePath, sanitizedLocation);
            } else if (Files.isRegularFile(basePath) && sanitizedLocation.endsWith(".drl")) {
                ruleFiles = List.of(basePath);
            } else {
                logger.warn("Location is neither a directory nor a .drl file: {}", basePath);
                return 0;
            }
        } catch (IOException e) {
            throw new RuleLoadingException(
                LOADER_TYPE, location, "Failed to scan directory: " + e.getMessage(), e);
        }

        int rulesLoaded = 0;
        for (int i = 0; i < ruleFiles.size(); i++) {
            Path ruleFile = ruleFiles.get(i);
            try {
                validateFileAccess(ruleFile);
                String content = Files.readString(ruleFile, StandardCharsets.UTF_8);
                validateRuleContent(content, ruleFile.toString());

                String kieResourcePath = generateKieResourcePath(location, i);
                kieFileSystem.write(kieResourcePath, content);

                logger.debug("Loaded rule file from filesystem: {} -> {}", ruleFile, kieResourcePath);
                rulesLoaded++;

            } catch (IOException e) {
                throw new RuleLoadingException(
                    LOADER_TYPE, ruleFile.toString(),
                    "Failed to read file: " + e.getMessage(), e);
            }
        }

        return rulesLoaded;
    }

    private Path resolveBasePath(String location) {
        String basePath = location;
        int globIndex = basePath.indexOf("**");
        if (globIndex > 0) {
            basePath = basePath.substring(0, globIndex);
        } else {
            globIndex = basePath.indexOf("*");
            if (globIndex > 0) {
                basePath = basePath.substring(0, globIndex);
            }
        }
        basePath = basePath.replaceAll("[/\\\\]+$", "");
        return Paths.get(basePath);
    }

    private List<Path> findRuleFilesInDirectory(Path basePath, String originalLocation)
            throws IOException {

        List<Path> ruleFiles = new ArrayList<>();
        boolean recursive = originalLocation.contains("**");

        if (recursive) {
            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                private int depth = 0;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (depth++ > MAX_DEPTH) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".drl")) {
                        ruleFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    depth--;
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            try (Stream<Path> paths = Files.list(basePath)) {
                paths.filter(path -> Files.isRegularFile(path))
                     .filter(path -> path.toString().endsWith(".drl"))
                     .forEach(ruleFiles::add);
            }
        }

        return ruleFiles;
    }

    private void validateFileAccess(Path file) throws RuleLoadingException {
        if (!Files.exists(file)) {
            throw new RuleLoadingException(LOADER_TYPE, file.toString(), "File does not exist");
        }
        if (!Files.isRegularFile(file)) {
            throw new RuleLoadingException(LOADER_TYPE, file.toString(), "Path is not a regular file");
        }
        if (!Files.isReadable(file)) {
            throw new RuleLoadingException(LOADER_TYPE, file.toString(), "File is not readable (check permissions)");
        }

        try {
            Path realPath = file.toRealPath();
            if (!realPath.equals(file.toAbsolutePath().normalize())) {
                logger.warn("File is a symbolic link: {} -> {}", file, realPath);
            }
        } catch (IOException e) {
            throw new RuleLoadingException(
                LOADER_TYPE, file.toString(), "Failed to resolve real path: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateConfiguration() throws RuleLoadingException {
        super.validateConfiguration();
        for (String location : locations) {
            sanitizePath(location);
        }
    }
}
