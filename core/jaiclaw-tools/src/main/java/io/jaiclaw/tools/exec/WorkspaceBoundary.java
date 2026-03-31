package io.jaiclaw.tools.exec;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Verifies that resolved file paths stay within the workspace directory.
 * Prevents path traversal attacks (e.g., "../../etc/passwd").
 */
public final class WorkspaceBoundary {

    private WorkspaceBoundary() {}

    /**
     * Resolve a file path relative to the workspace and verify it stays within bounds.
     *
     * @param workspaceDir the workspace root directory
     * @param filePath     the user-supplied file path (relative or absolute)
     * @return the resolved, normalized path guaranteed to be within the workspace
     * @throws SecurityException if the path escapes the workspace boundary
     */
    public static Path resolve(String workspaceDir, String filePath) {
        Path workspaceBase = Path.of(workspaceDir).toAbsolutePath().normalize();
        Path resolved = workspaceBase.resolve(filePath).normalize();

        if (!resolved.startsWith(workspaceBase)) {
            throw new SecurityException(
                    "Path traversal blocked: '" + filePath + "' resolves outside the workspace directory");
        }

        return resolved;
    }
}
