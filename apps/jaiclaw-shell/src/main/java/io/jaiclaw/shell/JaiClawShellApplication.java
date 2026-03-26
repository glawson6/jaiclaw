package io.jaiclaw.shell;

import com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = AgentPlatformAutoConfiguration.class)
public class JaiClawShellApplication {

    public static void main(String[] args) {
        SpringApplication.run(JaiClawShellApplication.class, args);
    }
}
