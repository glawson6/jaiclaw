package io.jaiclaw.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member as part of JaiClaw's public, stable API.
 *
 * <p>{@code @Stable} types and methods are committed-to. They will not
 * break across minor releases after JaiClaw 1.0; pre-1.0 they may
 * evolve, but with deprecation cycles when possible. New methods may be
 * added but existing signatures will not change incompatibly.
 *
 * <p>Adopters can safely import {@code @Stable} types into their own
 * code and rely on them across version upgrades.
 *
 * <p>0.8.0 P3.5; audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.5.
 *
 * @see Experimental
 * @see Internal
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Stable {
}
