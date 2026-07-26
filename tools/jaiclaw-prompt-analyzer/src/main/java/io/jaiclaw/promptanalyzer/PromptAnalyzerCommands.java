package io.jaiclaw.promptanalyzer;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.shell.core.command.annotation.Option;

import java.nio.file.Path;

/**
 * Spring Shell commands for analyzing JaiClaw project prompt token usage.
 */
@Component
public class PromptAnalyzerCommands {

    private final ProjectScanner scanner = new ProjectScanner();

    @Command(name = "prompt-analyze", alias = "prompt analyze", description = "Analyze a JaiClaw project and estimate input token usage")
    public String analyze(
            @Option(longName = "path", defaultValue = ".") String projectPath
    ) {
        try {
            AnalysisReport report = scanner.analyze(Path.of(projectPath).toAbsolutePath());
            return report.format();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Command(name = "prompt-check", alias = "prompt check", description = "Check if estimated token usage is below a threshold")
    public String check(
            @Option(longName = "path", defaultValue = ".") String projectPath,
            @Option(longName = "threshold", defaultValue = "5000") int threshold
    ) {
        try {
            AnalysisReport report = scanner.analyze(Path.of(projectPath).toAbsolutePath());
            if (report.estimatedTotalTokens() <= threshold) {
                return String.format("PASS: estimated %,d tokens (threshold: %,d)",
                        report.estimatedTotalTokens(), threshold);
            } else {
                return String.format("FAIL: estimated %,d tokens exceeds threshold of %,d\n\n%s",
                        report.estimatedTotalTokens(), threshold, report.format());
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
