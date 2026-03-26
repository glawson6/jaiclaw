package io.jaiclaw.cronmanager;

import com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration;
import io.jaiclaw.autoconfigure.JaiClawChannelAutoConfiguration;
import io.jaiclaw.autoconfigure.JaiClawGatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        AgentPlatformAutoConfiguration.class,
        JaiClawGatewayAutoConfiguration.class,
        JaiClawChannelAutoConfiguration.class
})
public class CronManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CronManagerApplication.class, args);
    }
}
