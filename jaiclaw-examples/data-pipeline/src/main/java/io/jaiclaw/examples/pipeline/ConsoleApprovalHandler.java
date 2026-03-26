package io.jaiclaw.examples.pipeline;

import io.jaiclaw.core.agent.ToolApprovalDecision;
import io.jaiclaw.core.agent.ToolApprovalHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interactive console-based approval handler for destructive ETL operations.
 * Reads Y/N from {@link System#console()} for human-in-the-loop approval.
 */
@Component
public class ConsoleApprovalHandler implements ToolApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsoleApprovalHandler.class);

    @Override
    public CompletableFuture<ToolApprovalDecision> requestApproval(
            String toolName, Map<String, Object> parameters, String sessionKey) {

        System.out.printf("%n--- APPROVAL REQUIRED ---%n");
        System.out.printf("Tool:    %s%n", toolName);
        System.out.printf("Params:  %s%n", parameters);
        System.out.printf("Session: %s%n", sessionKey);
        System.out.print("Approve? (y/n): ");

        var console = System.console();
        if (console != null) {
            String input = console.readLine().trim().toLowerCase();
            if ("y".equals(input) || "yes".equals(input)) {
                log.info("Tool '{}' approved by user", toolName);
                return CompletableFuture.completedFuture(new ToolApprovalDecision.Approved());
            }
        }

        log.info("Tool '{}' denied by user", toolName);
        return CompletableFuture.completedFuture(
                new ToolApprovalDecision.Denied("User denied approval for " + toolName));
    }
}
