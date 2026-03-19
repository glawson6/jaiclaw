package io.jclaw.code;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;
import io.jclaw.tools.builtin.AbstractBuiltinTool;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * File pattern matching tool inspired by Claude Code's Glob tool.
 * Finds files matching a glob pattern (e.g., {@code ** / *.java}).
 */
public class GlobTool extends AbstractBuiltinTool {

    private static final int MAX_RESULTS = 200;

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", "target", "build", "node_modules", ".idea", ".gradle", ".svn", ".hg");

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "pattern": {
                  "type": "string",
                  "description": "Glob pattern to match files (e.g., '**/*.java', 'src/**/*.groovy')"
                },
                "path": {
                  "type": "string",
                  "description": "Directory to search in, relative to workspace. Defaults to workspace root."
                }
              },
              "required": ["pattern"]
            }""";

    public GlobTool() {
        super(new ToolDefinition(
                "glob",
                "Find files matching a glob pattern. Returns matching file paths sorted alphabetically.",
                ToolCatalog.SECTION_FILES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.MINIMAL, ToolProfile.CODING, ToolProfile.FULL)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String pattern = requireParam(parameters, "pattern");
        String basePath = optionalParam(parameters, "path", "");

        Path baseDir = Path.of(context.workspaceDir()).resolve(basePath).normalize();

        if (!Files.isDirectory(baseDir)) {
            return new ToolResult.Error("Directory not found: " + basePath);
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        // Java's ** doesn't match zero directory components for root-level files.
        // If the pattern starts with **/, also try the remainder against root-level files.
        PathMatcher rootFallback = pattern.startsWith("**/")
                ? FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3))
                : null;
        List<String> matches = new ArrayList<>();

        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (EXCLUDED_DIRS.contains(dir.getFileName().toString()) && !dir.equals(baseDir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relative = baseDir.relativize(file);
                if (matcher.matches(relative)
                        || (rootFallback != null && relative.getNameCount() == 1
                            && rootFallback.matches(relative))) {
                    matches.add(relative.toString());
                    if (matches.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        if (matches.isEmpty()) {
            return new ToolResult.Success("No files matched pattern: " + pattern);
        }

        Collections.sort(matches);
        String result = matches.stream().collect(Collectors.joining("\n"));
        if (matches.size() >= MAX_RESULTS) {
            result += "\n... (results capped at " + MAX_RESULTS + ")";
        }
        return new ToolResult.Success(result);
    }
}
