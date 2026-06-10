package io.jaiclaw.examples.aiops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AIOps incident responder. Six-stage pipeline triggered by a Camel route
 * ({@code direct:incident-alert}) that triages an alert, attempts auto-
 * remediation, and drafts a 5-Whys post-mortem.
 *
 * <p>Driven via the Spring Shell — see {@link AiopsIncidentResponderShellCommands}.
 */
@SpringBootApplication
public class AiopsIncidentResponderApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiopsIncidentResponderApplication.class, args);
    }
}
