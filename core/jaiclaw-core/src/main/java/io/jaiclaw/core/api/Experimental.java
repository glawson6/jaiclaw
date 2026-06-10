package io.jaiclaw.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member as part of JaiClaw's public, but evolving API.
 *
 * <p>{@code @Experimental} surfaces are intentionally exposed to
 * adopters but may change shape — signatures may evolve, fields may be
 * renamed, behaviour may shift — at any release boundary, including
 * minor releases after 1.0. Use these surfaces at your own risk and be
 * prepared to update your code.
 *
 * <p>Typed hook events ({@link io.jaiclaw.core.hook.event.HookEvent} and
 * its 16 subtypes), {@link io.jaiclaw.core.tool.param.TypedToolCallback},
 * and {@link io.jaiclaw.channel.AbstractChannelAdapter} all start out
 * {@code @Experimental} in 0.8.0 and graduate to {@link Stable} once
 * the community shape settles.
 *
 * <p>0.8.0 P3.5; audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.5.
 *
 * @see Stable
 * @see Internal
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Experimental {
}
