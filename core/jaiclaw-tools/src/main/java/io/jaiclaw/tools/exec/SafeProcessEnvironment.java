package io.jaiclaw.tools.exec;

import java.util.Map;
import java.util.Set;

/**
 * Sanitizes the environment of a {@link ProcessBuilder} to prevent leaking
 * sensitive variables (API keys, tokens, database credentials) to spawned processes.
 *
 * <p>Clears all inherited environment variables and restores only a minimal safe set
 * required for command execution (PATH, HOME, LANG, TERM, USER, SHELL, TMPDIR).
 */
public final class SafeProcessEnvironment {

    private SafeProcessEnvironment() {}

    /** Environment variable names safe to inherit. */
    private static final Set<String> SAFE_VARS = Set.of(
            "PATH", "HOME", "LANG", "LC_ALL", "LC_CTYPE",
            "TERM", "USER", "LOGNAME", "SHELL", "TMPDIR",
            "TZ", "HOSTNAME",
            // Kubernetes tools need KUBECONFIG
            "KUBECONFIG"
    );

    /**
     * Clear the process builder's environment and restore only safe variables
     * from the current process environment.
     */
    public static void apply(ProcessBuilder pb) {
        Map<String, String> currentEnv = System.getenv();
        Map<String, String> processEnv = pb.environment();
        processEnv.clear();

        for (String key : SAFE_VARS) {
            String value = currentEnv.get(key);
            if (value != null) {
                processEnv.put(key, value);
            }
        }
    }
}
