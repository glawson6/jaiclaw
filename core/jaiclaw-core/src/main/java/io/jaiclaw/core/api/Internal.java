package io.jaiclaw.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member as JaiClaw-internal — usable from outside the
 * framework's own code only at the caller's own risk.
 *
 * <p>{@code @Internal} surfaces carry <b>no compatibility guarantees of
 * any kind</b>. They may be removed, renamed, or have their behaviour
 * changed at any release boundary, including patch releases.
 *
 * <p>Where possible, {@code @Internal} types also live under an
 * {@code .internal.*} subpackage so the package name itself signals
 * "don't import me." When that move would cause too much downstream
 * churn, the annotation alone is the marker.
 *
 * <p>0.8.0 P3.5; audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.5.
 *
 * @see Stable
 * @see Experimental
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Internal {
}
