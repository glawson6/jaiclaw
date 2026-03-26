package io.jaiclaw.cli.architect;

/**
 * Determines how the generated CLI project is structured.
 */
public enum ProjectMode {
    /** Sub-module inside the JaiClaw repository, inherits jaiclaw-parent. */
    JAICLAW_SUBMODULE,
    /** Sub-module inside another Maven project, imports jaiclaw-bom. */
    EXTERNAL_SUBMODULE,
    /** Standalone Spring Boot project with spring-boot-starter-parent. */
    STANDALONE,
    /** Single-file JBang script with //DEPS directives. */
    JBANG
}
