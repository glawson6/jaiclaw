package io.jclaw.cronmanager;

import com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration;
import io.jclaw.autoconfigure.JClawChannelAutoConfiguration;
import io.jclaw.autoconfigure.JClawGatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        AgentPlatformAutoConfiguration.class,
        JClawGatewayAutoConfiguration.class,
        JClawChannelAutoConfiguration.class
})
public class CronManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CronManagerApplication.class, args);
    }
}
