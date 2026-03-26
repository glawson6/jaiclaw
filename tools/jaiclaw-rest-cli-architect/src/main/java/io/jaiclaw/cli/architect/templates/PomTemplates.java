package io.jaiclaw.cli.architect.templates;

import io.jaiclaw.cli.architect.ProjectMode;
import io.jaiclaw.cli.architect.ProjectSpec;

/**
 * Text block templates for pom.xml variants based on {@link ProjectMode}.
 */
public final class PomTemplates {

    private PomTemplates() {}

    public static String generate(ProjectSpec spec) {
        return switch (spec.mode()) {
            case JAICLAW_SUBMODULE -> jaiclawSubmodule(spec);
            case EXTERNAL_SUBMODULE -> externalSubmodule(spec);
            case STANDALONE -> standalone(spec);
            case JBANG -> ""; // No POM for JBang mode
        };
    }

    private static String jaiclawSubmodule(ProjectSpec spec) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>io.jaiclaw</groupId>
                        <artifactId>jaiclaw-parent</artifactId>
                        <version>0.1.0-SNAPSHOT</version>
                    </parent>

                    <artifactId>jaiclaw-cli-%s</artifactId>
                    <name>JaiClaw CLI: %s</name>

                    <dependencies>
                        <dependency>
                            <groupId>io.jaiclaw</groupId>
                            <artifactId>jaiclaw-core</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.shell</groupId>
                            <artifactId>spring-shell-starter</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <scope>provided</scope>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>

                        <!-- Test -->
                        <dependency>
                            <groupId>org.apache.groovy</groupId>
                            <artifactId>groovy</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.spockframework</groupId>
                            <artifactId>spock-core</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.codehaus.gmavenplus</groupId>
                                <artifactId>gmavenplus-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(spec.name(), displayName(spec));
    }

    private static String externalSubmodule(ProjectSpec spec) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>%s</groupId>
                    <artifactId>%s-cli</artifactId>
                    <version>0.1.0-SNAPSHOT</version>

                    <name>%s CLI</name>

                    <properties>
                        <java.version>21</java.version>
                        <maven.compiler.source>${java.version}</maven.compiler.source>
                        <maven.compiler.target>${java.version}</maven.compiler.target>
                    </properties>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.jaiclaw</groupId>
                                <artifactId>jaiclaw-bom</artifactId>
                                <version>0.1.0-SNAPSHOT</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>3.5.6</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.shell</groupId>
                                <artifactId>spring-shell-dependencies</artifactId>
                                <version>3.4.0</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>io.jaiclaw</groupId>
                            <artifactId>jaiclaw-core</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.shell</groupId>
                            <artifactId>spring-shell-starter</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(spec.groupId(), spec.name(), displayName(spec));
    }

    private static String standalone(ProjectSpec spec) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.6</version>
                        <relativePath/>
                    </parent>

                    <groupId>%s</groupId>
                    <artifactId>%s-cli</artifactId>
                    <version>0.1.0-SNAPSHOT</version>

                    <name>%s CLI</name>

                    <properties>
                        <java.version>21</java.version>
                    </properties>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.jaiclaw</groupId>
                                <artifactId>jaiclaw-bom</artifactId>
                                <version>0.1.0-SNAPSHOT</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.shell</groupId>
                                <artifactId>spring-shell-dependencies</artifactId>
                                <version>3.4.0</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>io.jaiclaw</groupId>
                            <artifactId>jaiclaw-core</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.shell</groupId>
                            <artifactId>spring-shell-starter</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <configuration>
                                    <mainClass>%s.%sCliApplication</mainClass>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(
                spec.groupId(), spec.name(), displayName(spec),
                spec.packageName(), capitalize(spec.name()));
    }

    private static String displayName(ProjectSpec spec) {
        if (spec.api() != null && spec.api().title() != null) {
            return spec.api().title();
        }
        return capitalize(spec.name());
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
