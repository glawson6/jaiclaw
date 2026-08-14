package io.jaiclaw.compliance.gdpr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Role configuration for the GDPR REST controller ({@link GdprController}).
 * Referenced from {@code @PreAuthorize} SpEL on Article 15 / 17 endpoints
 * via the {@link GdprAuthzExpressions} bean.
 *
 * <p>Config keys:
 * <pre>{@code
 * jaiclaw.compliance.gdpr.roles.operator: GDPR_OPERATOR   # default
 * }</pre>
 *
 * <p>Setting the role to blank ({@code operator: ""}) short-circuits the
 * check to "any authenticated principal" — useful for adopters rolling out
 * the module before their role hierarchy is designed. When Spring Security
 * is absent from the classpath, the {@code @PreAuthorize} annotations are
 * silently inert.
 *
 * <p>One public constructor per {@code @ConfigurationProperties} record
 * (Spring Boot 4 record-binder rule from the framework CLAUDE.md).
 * Programmatic defaults live on {@link Roles#defaults()}, not on an
 * overload constructor.
 */
@ConfigurationProperties(prefix = "jaiclaw.compliance.gdpr")
public record GdprAuthzProperties(Roles roles) {

    public GdprAuthzProperties {
        if (roles == null) roles = Roles.defaults();
    }

    /**
     * Role names checked by {@link GdprAuthzExpressions}. Each field maps
     * to one method on that bean. Blank value → any authenticated principal
     * may invoke.
     */
    public record Roles(String operator) {

        public static final Roles DEFAULT = new Roles("GDPR_OPERATOR");

        public Roles {
            if (operator == null) operator = "";
        }

        /** Programmatic default — the binder never sees this. */
        public static Roles defaults() {
            return DEFAULT;
        }
    }
}
