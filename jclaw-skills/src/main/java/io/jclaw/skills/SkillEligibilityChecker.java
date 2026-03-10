package io.jclaw.skills;

import io.jclaw.core.skill.SkillDefinition;
import io.jclaw.core.skill.SkillMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Checks whether a skill is eligible to run on the current platform.
 * Validates platform compatibility and required binary availability.
 */
public class SkillEligibilityChecker {

    private static final Logger log = LoggerFactory.getLogger(SkillEligibilityChecker.class);

    private final String currentPlatform;

    public SkillEligibilityChecker() {
        this(System.getProperty("os.name", "").toLowerCase().contains("mac") ? "darwin"
                : System.getProperty("os.name", "").toLowerCase().contains("win") ? "windows"
                : "linux");
    }

    public SkillEligibilityChecker(String currentPlatform) {
        this.currentPlatform = currentPlatform;
    }

    public boolean isEligible(SkillDefinition skill) {
        SkillMetadata meta = skill.metadata();

        // Check platform
        if (!meta.supportedPlatforms().isEmpty()
                && !meta.supportedPlatforms().contains(currentPlatform)) {
            log.debug("Skill '{}' not eligible: platform '{}' not in {}",
                    skill.name(), currentPlatform, meta.supportedPlatforms());
            return false;
        }

        // Check required binaries
        for (String bin : meta.requiredBins()) {
            if (!isBinaryAvailable(bin)) {
                log.debug("Skill '{}' not eligible: required binary '{}' not found",
                        skill.name(), bin);
                return false;
            }
        }

        return true;
    }

    private boolean isBinaryAvailable(String binary) {
        try {
            var process = new ProcessBuilder("which", binary)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
