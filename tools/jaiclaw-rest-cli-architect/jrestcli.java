///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS io.jaiclaw:jaiclaw-rest-cli-architect:0.1.0-SNAPSHOT

import io.jaiclaw.cli.architect.JRestCliApplication;
import org.springframework.boot.SpringApplication;

/**
 * JBang launcher for the REST CLI Architect standalone mode.
 * Requires the standalone JAR to be installed in the local Maven repo:
 *   ./mvnw install -pl jaiclaw-rest-cli-architect -Pstandalone -DskipTests
 *
 * Usage:
 *   jbang jrestcli.java scaffold --spec acme.json
 *   jbang jrestcli.java validate --spec acme.json
 *   jbang jrestcli.java from-openapi --url https://api.example.com/openapi.json
 */
public class jrestcli {
    public static void main(String[] args) {
        SpringApplication.run(JRestCliApplication.class, args);
    }
}
