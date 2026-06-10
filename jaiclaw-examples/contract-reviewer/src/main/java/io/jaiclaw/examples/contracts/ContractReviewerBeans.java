package io.jaiclaw.examples.contracts;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stub PROCESSOR bean for the contract-reviewer pipeline. The
 * {@code contractRouter} parses the risk-score digit out of an upstream
 * stage's output and emits a {@code ROUTE: …} marker plus a one-line
 * summary suitable for the shell's {@code last-result}.
 */
@Configuration
public class ContractReviewerBeans {

    public static final Path HOME = Path.of(System.getProperty("user.home"), ".jaiclaw", "contract-reviewer");
    public static final Path INBOX = HOME.resolve("inbox");

    private static final Pattern RISK_PATTERN = Pattern.compile("risk_score\\s*:\\s*(\\d+)");

    @PostConstruct
    void initDirs() throws IOException {
        Files.createDirectories(INBOX);
    }

    /**
     * Routes the contract to AUTO_APPROVE / COUNSEL_REVIEW / REJECT based on
     * the {@code risk_score: N} marker emitted by the risk-score AGENT.
     */
    @Bean
    public Function<String, String> contractRouter() {
        return body -> {
            String b = body == null ? "" : body;
            int risk = extractRisk(b);
            String route;
            if (risk <= 3) {
                route = "ROUTE: AUTO_APPROVE";
            } else if (risk <= 7) {
                route = "ROUTE: COUNSEL_REVIEW";
            } else {
                route = "ROUTE: REJECT";
            }
            return b + "\n" + route
                    + "\nsummary: risk=" + risk + ", route=" + route.substring(7);
        };
    }

    private static int extractRisk(String body) {
        Matcher m = RISK_PATTERN.matcher(body);
        if (!m.find()) return 10; // failure-closed: assume max risk
        try {
            int v = Integer.parseInt(m.group(1));
            return Math.max(1, Math.min(10, v));
        } catch (NumberFormatException ex) {
            return 10;
        }
    }
}
