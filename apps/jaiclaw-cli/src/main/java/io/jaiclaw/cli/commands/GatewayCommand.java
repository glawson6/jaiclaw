package io.jaiclaw.cli.commands;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.shell.core.command.annotation.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gateway lifecycle commands — start, stop, and status of the JaiClaw gateway server.
 */
@Component
public class GatewayCommand {

    @Command(name = "gateway start", alias = "gateway-start", description = "Start the JaiClaw gateway server")
    public String start(
            @Option(defaultValue = "8080", description = "Server port") int port,
            @Option(defaultValue = "false", description = "Run in Docker mode") boolean docker) {

        if (docker) {
            return startDocker(port);
        }
        return startLocal(port);
    }

    @Command(name = "gateway stop", alias = "gateway-stop", description = "Stop the JaiClaw gateway server")
    public String stop() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "down");
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0 ? "Gateway stopped." : "Failed to stop gateway (exit code " + exitCode + ")";
        } catch (IOException | InterruptedException e) {
            return "Error stopping gateway: " + e.getMessage();
        }
    }

    @Command(name = "gateway status", alias = "gateway-status", description = "Show gateway server status")
    public String status() {
        try {
            ProcessBuilder pb = new ProcessBuilder("curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                    "--connect-timeout", "2", "http://localhost:8080/actuator/health");
            Process process = pb.start();
            int exitCode = process.waitFor();
            String httpCode = new String(process.getInputStream().readAllBytes()).trim();

            if (exitCode == 0 && "200".equals(httpCode)) {
                return "Gateway is running (port 8080)";
            }
            return "Gateway is not running or not reachable";
        } catch (IOException | InterruptedException e) {
            return "Gateway is not running or not reachable";
        }
    }

    private String startLocal(int port) {
        // Find the gateway JAR
        Path projectRoot = findProjectRoot();
        if (projectRoot == null) {
            return "Cannot find JaiClaw project root. Run from the project directory or set JAICLAW_HOME.";
        }

        String javaHome = System.getenv("JAVA_HOME");
        String java = javaHome != null ? javaHome + "/bin/java" : "java";

        Path gatewayJar = projectRoot.resolve("apps/jaiclaw-gateway-app/target");
        if (!gatewayJar.toFile().isDirectory()) {
            return "Gateway JAR not found. Build first: ./mvnw package -pl :jaiclaw-gateway-app -am -DskipTests";
        }

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(java);
            cmd.add("-Dserver.port=" + port);
            cmd.add("-jar");
            // Find the JAR file
            String[] jars = gatewayJar.toFile().list((dir, name) ->
                    name.startsWith("jaiclaw-gateway-app") && name.endsWith(".jar") && !name.contains("original"));
            if (jars == null || jars.length == 0) {
                return "Gateway JAR not found in " + gatewayJar;
            }
            cmd.add(gatewayJar.resolve(jars[0]).toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            pb.start();
            return "Gateway starting on port " + port + "...";
        } catch (IOException e) {
            return "Error starting gateway: " + e.getMessage();
        }
    }

    private String startDocker(int port) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.add("compose");
            cmd.add("up");
            cmd.add("-d");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0
                    ? "Gateway started in Docker on port " + port
                    : "Failed to start gateway in Docker (exit code " + exitCode + ")";
        } catch (IOException | InterruptedException e) {
            return "Error starting Docker gateway: " + e.getMessage();
        }
    }

    private Path findProjectRoot() {
        // Check JAICLAW_PROJECT_ROOT, then try walking up from CWD
        String root = System.getenv("JAICLAW_PROJECT_ROOT");
        if (root != null) {
            return Path.of(root);
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path candidate = cwd;
        while (candidate != null) {
            if (candidate.resolve("pom.xml").toFile().exists()
                    && candidate.resolve("apps/jaiclaw-gateway-app").toFile().isDirectory()) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }
}
