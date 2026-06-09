package io.jaiclaw.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"io.jaiclaw.cli", "io.jaiclaw.shell"})
public class JaiClawCliApplication {

    public static void main(String[] args) {
        SpringApplication.run(JaiClawCliApplication.class, args);
    }
}
