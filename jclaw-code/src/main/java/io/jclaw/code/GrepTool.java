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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Content search tool inspired by Claude Code's Grep tool.
 * Searches file contents for a regex pattern.
 */
public class GrepTool extends AbstractBuiltinTool {

    private static final int DEFAULT_MAX_RESULTS = 50;

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", "target", "build", "node_modules", ".idea", ".gradle", ".svn", ".hg");

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "pattern": {
                  "type": "string",
                  "description": "Regex pattern to search for in file contents"
                },
                "path": {
                  "type": "string",
                  "description": "Directory or file to search in, relative to workspace. Defaults to workspace root."
                },
                "glob": {
                  "type": "string",
                  "description": "Glob filter to restrict which files are searched (e.g., '*.java')"
                },
                "context": {
                  "type": "integer",
                  "description": "Number of context lines to show before and after each match. Defaults to 0."
                },
                "max_results": {
                  "type": "integer",
                  "description": "Maximum number of matches to return. Defaults to 50."
                }
              },
              "required": ["pattern"]
            }""";

    public GrepTool() {
        super(new ToolDefinition(
                "grep",
                "Search file contents for a regex pattern. Returns matching lines with file paths and line numbers.",
                ToolCatalog.SECTION_FILES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.MINIMAL, ToolProfile.CODING, ToolProfile.FULL)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String patternStr = requireParam(parameters, "pattern");
        String basePath = optionalParam(parameters, "path", "");
        String globFilter = optionalParam(parameters, "glob", null);
        int contextLines = parameters.containsKey("context")
                ? ((Number) parameters.get("context")).intValue() : 0;
        int maxResults = parameters.containsKey("max_results")
                ? ((Number) parameters.get("max_results")).intValue() : DEFAULT_MAX_RESULTS;

        Pattern regex;
        try {
            regex = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            return new ToolResult.Error("Invalid regex: " + e.getMessage());
        }

        Path baseDir = Path.of(context.workspaceDir()).resolve(basePath).normalize();

        if (!Files.exists(baseDir)) {
            return new ToolResult.Error("Path not found: " + basePath);
        }

        PathMatcher globMatcher = globFilter != null
                ? FileSystems.getDefault().getPathMatcher("glob:" + globFilter)
                : null;

        List<String> results = new ArrayList<>();

        if (Files.isRegularFile(baseDir)) {
            searchFile(baseDir, baseDir.getParent(), regex, contextLines, maxResults, results);
        } else {
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
                    if (globMatcher != null && !globMatcher.matches(file.getFileName())) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isBinary(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    searchFile(file, baseDir, regex, contextLines, maxResults, results);
                    if (results.size() >= maxResults) {
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        if (results.isEmpty()) {
            return new ToolResult.Success("No matches found for: " + patternStr);
        }

        StringBuilder sb = new StringBuilder();
        for (String line : results) {
            sb.append(line).append('\n');
        }
        if (results.size() >= maxResults) {
            sb.append("... (results capped at ").append(maxResults).append(")");
        }
        return new ToolResult.Success(sb.toString().stripTrailing());
    }

    private static void searchFile(Path file, Path baseDir, Pattern regex,
                                    int contextLines, int maxResults, List<String> results) {
        try {
            List<String> lines = Files.readAllLines(file);
            String relativePath = baseDir.relativize(file).toString();

            for (int i = 0; i < lines.size() && results.size() < maxResults; i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    int start = Math.max(0, i - contextLines);
                    int end = Math.min(lines.size() - 1, i + contextLines);

                    // Add context-before lines
                    for (int j = start; j < i; j++) {
                        results.add(relativePath + ":" + (j + 1) + "- " + lines.get(j));
                        if (results.size() >= maxResults) return;
                    }
                    // Add the matching line
                    results.add(relativePath + ":" + (i + 1) + ": " + lines.get(i));
                    if (results.size() >= maxResults) return;
                    // Add context-after lines
                    for (int j = i + 1; j <= end; j++) {
                        results.add(relativePath + ":" + (j + 1) + "- " + lines.get(j));
                        if (results.size() >= maxResults) return;
                    }
                }
            }
        } catch (IOException e) {
            // Skip files that can't be read
        }
    }

    private static boolean isBinary(Path file) {
        try {
            byte[] bytes = new byte[512];
            try (var is = Files.newInputStream(file)) {
                int read = is.read(bytes);
                if (read <= 0) return false;
                for (int i = 0; i < read; i++) {
                    if (bytes[i] == 0) return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
