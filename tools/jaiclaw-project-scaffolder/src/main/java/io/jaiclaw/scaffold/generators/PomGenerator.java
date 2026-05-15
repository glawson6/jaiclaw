package io.jaiclaw.scaffold.generators;

import io.jaiclaw.scaffold.KnownModules;
import io.jaiclaw.scaffold.ProjectManifest;
import io.jaiclaw.scaffold.ProjectManifest.Archetype;
import io.jaiclaw.scaffold.ProjectManifest.ParentMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates pom.xml for a JaiClaw project. Supports two parent modes:
 * <ul>
 *   <li>{@code STANDALONE} — spring-boot-starter-parent + jaiclaw-bom import (self-contained)</li>
 *   <li>{@code JAICLAW} — jaiclaw-parent as Maven parent (inherits managed versions and plugins)</li>
 * </ul>
 */
public final class PomGenerator {

    private PomGenerator() {}

    public static String generate(ProjectManifest manifest) {
        return manifest.parentMode() == ParentMode.JAICLAW
                ? generateJaiclawParent(manifest)
                : generateStandalone(manifest);
    }

    // --- STANDALONE mode: spring-boot-starter-parent + jaiclaw-bom ---

    private static String generateStandalone(ProjectManifest manifest) {
        var sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.14</version>
                        <relativePath/>
                    </parent>

                """);

        sb.append("    <groupId>").append(manifest.groupId()).append("</groupId>\n");
        sb.append("    <artifactId>").append(manifest.artifactId()).append("</artifactId>\n");
        sb.append("    <version>").append(manifest.version()).append("</version>\n");
        sb.append("    <name>").append(ProjectManifest.toPascalCase(manifest.name())).append("</name>\n");
        sb.append("    <description>").append(escapeXml(manifest.description())).append("</description>\n");
        sb.append("\n");

        // Properties
        sb.append("    <properties>\n");
        sb.append("        <java.version>21</java.version>\n");
        sb.append("    </properties>\n\n");

        // Dependency management — BOM import
        sb.append("""
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.jaiclaw</groupId>
                                <artifactId>jaiclaw-bom</artifactId>
                """);
        sb.append("                <version>").append(manifest.jaiclawVersion()).append("</version>\n");
        sb.append("""
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                """);

        // Dependencies
        sb.append("    <dependencies>\n");
        appendDependencies(sb, manifest);

        // Test dependencies — standalone must declare versions explicitly
        sb.append("""

                        <!-- Test -->
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.groovy</groupId>
                            <artifactId>groovy</artifactId>
                            <version>4.0.24</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.spockframework</groupId>
                            <artifactId>spock-core</artifactId>
                            <version>2.3-groovy-4.0</version>
                            <scope>test</scope>
                        </dependency>
                """);

        sb.append("    </dependencies>\n\n");

        // Build section — standalone must declare all plugin versions
        sb.append("    <build>\n");
        sb.append("        <plugins>\n");
        sb.append("""
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                            <plugin>
                                <groupId>org.codehaus.gmavenplus</groupId>
                                <artifactId>gmavenplus-plugin</artifactId>
                                <version>3.0.2</version>
                                <executions>
                                    <execution>
                                        <goals>
                                            <goal>compileTests</goal>
                                        </goals>
                                    </execution>
                                </executions>
                            </plugin>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <configuration>
                                    <includes>
                                        <include>**/*Spec.java</include>
                                    </includes>
                                </configuration>
                            </plugin>
                """);
        sb.append("        </plugins>\n");
        sb.append("    </build>\n");

        // Docker / JKube profile
        if (manifest.docker().enabled()) {
            sb.append("\n");
            sb.append(jkubeProfile(manifest));
        }

        sb.append("</project>\n");
        return sb.toString();
    }

    // --- JAICLAW mode: jaiclaw-parent as Maven parent ---

    private static String generateJaiclawParent(ProjectManifest manifest) {
        var sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>io.jaiclaw</groupId>
                        <artifactId>jaiclaw-parent</artifactId>
                """);
        sb.append("        <version>").append(manifest.jaiclawVersion()).append("</version>\n");
        sb.append("""
                        <relativePath/>
                    </parent>

                """);

        sb.append("    <artifactId>").append(manifest.artifactId()).append("</artifactId>\n");
        sb.append("    <name>").append(ProjectManifest.toPascalCase(manifest.name())).append("</name>\n");
        sb.append("    <description>").append(escapeXml(manifest.description())).append("</description>\n");
        sb.append("\n");

        // No <properties> block — java.version inherited from jaiclaw-parent
        // No <dependencyManagement> — all versions managed by jaiclaw-parent

        // Dependencies
        sb.append("    <dependencies>\n");
        appendDependencies(sb, manifest);
        sb.append("    </dependencies>\n\n");

        // Build section — lean: no gmavenplus/surefire config (inherited from parent)
        sb.append("    <build>\n");
        sb.append("        <plugins>\n");
        sb.append("""
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                            <plugin>
                                <groupId>io.jaiclaw</groupId>
                                <artifactId>jaiclaw-maven-plugin</artifactId>
                                <version>${project.version}</version>
                                <executions>
                                    <execution>
                                        <goals><goal>analyze</goal></goals>
                                    </execution>
                                </executions>
                            </plugin>
                """);
        sb.append("        </plugins>\n");
        sb.append("    </build>\n");

        // No JKube profile — inherited from jaiclaw-parent's k8s profile

        sb.append("</project>\n");
        return sb.toString();
    }

    // --- Shared dependency generation ---

    private static void appendDependencies(StringBuilder sb, ProjectManifest manifest) {
        // Core dependencies per archetype
        for (var dep : coreDependencies(manifest)) {
            sb.append(dep);
        }

        // AI provider dependencies
        for (var dep : aiProviderDependencies(manifest)) {
            sb.append(dep);
        }

        // Extension dependencies
        for (String ext : manifest.extensions()) {
            sb.append(jaiclawDep("jaiclaw-" + ext));
        }

        // Channel dependencies
        for (String ch : manifest.channels()) {
            sb.append(jaiclawDep("jaiclaw-channel-" + ch));
        }
    }

    private static List<String> coreDependencies(ProjectManifest manifest) {
        List<String> deps = new ArrayList<>();
        switch (manifest.archetype()) {
            case GATEWAY -> {
                deps.add(jaiclawDep("jaiclaw-spring-boot-starter"));
                deps.add(jaiclawDep("jaiclaw-gateway"));
                deps.add(springBootDep("spring-boot-starter-web"));
            }
            case EMBABEL -> {
                deps.add(jaiclawDep("jaiclaw-spring-boot-starter"));
                deps.add(jaiclawDep("jaiclaw-gateway"));
                deps.add(jaiclawDep("jaiclaw-embabel-delegate"));
                deps.add(springBootDep("spring-boot-starter-web"));
            }
            case CAMEL -> {
                deps.add(jaiclawDep("jaiclaw-spring-boot-starter"));
                deps.add(jaiclawDep("jaiclaw-gateway"));
                deps.add(jaiclawDep("jaiclaw-camel"));
                deps.add(springBootDep("spring-boot-starter-web"));
            }
            case COMPREHENSIVE -> {
                deps.add(jaiclawDep("jaiclaw-spring-boot-starter"));
                deps.add(jaiclawDep("jaiclaw-gateway"));
                deps.add(jaiclawDep("jaiclaw-security"));
                deps.add(jaiclawDep("jaiclaw-documents"));
                deps.add(jaiclawDep("jaiclaw-media"));
                deps.add(springBootDep("spring-boot-starter-web"));
            }
            case MINIMAL -> {
                deps.add(jaiclawDep("jaiclaw-spring-boot-starter"));
                deps.add(springBootDep("spring-boot-starter-web"));
            }
        }
        return deps;
    }

    private static List<String> aiProviderDependencies(ProjectManifest manifest) {
        Set<String> artifacts = new LinkedHashSet<>();
        artifacts.add(KnownModules.springAiStarterArtifact(manifest.aiProvider().primary()));
        for (String p : manifest.aiProvider().additional()) {
            artifacts.add(KnownModules.springAiStarterArtifact(p));
        }
        return artifacts.stream()
                .map(PomGenerator::springAiDep)
                .toList();
    }

    private static String jaiclawDep(String artifactId) {
        return """
                        <dependency>
                            <groupId>io.jaiclaw</groupId>
                            <artifactId>%s</artifactId>
                        </dependency>
                """.formatted(artifactId);
    }

    private static String springBootDep(String artifactId) {
        return """
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>%s</artifactId>
                        </dependency>
                """.formatted(artifactId);
    }

    private static String springAiDep(String artifactId) {
        return """
                        <dependency>
                            <groupId>org.springframework.ai</groupId>
                            <artifactId>%s</artifactId>
                        </dependency>
                """.formatted(artifactId);
    }

    private static String jkubeProfile(ProjectManifest manifest) {
        return """
                    <profiles>
                        <profile>
                            <id>k8s</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.eclipse.jkube</groupId>
                                        <artifactId>kubernetes-maven-plugin</artifactId>
                                        <version>1.17.0</version>
                                        <configuration>
                                            <images>
                                                <image>
                                                    <name>%s/%s:${project.version}</name>
                                                    <build>
                                                        <from>%s</from>
                                                        <assembly>
                                                            <targetDir>/app</targetDir>
                                                            <descriptorRef>artifact</descriptorRef>
                                                        </assembly>
                                                        <cmd>java -jar /app/${project.artifactId}-${project.version}.jar</cmd>
                                                    </build>
                                                </image>
                                            </images>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                    </profiles>
                """.formatted(manifest.groupId(), manifest.artifactId(), manifest.docker().baseImage());
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
