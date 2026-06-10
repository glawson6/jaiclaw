/**
 * Sealed typed hook event hierarchy shipped in 0.8.0 P3.1.
 *
 * <p>{@link io.jaiclaw.core.hook.event.HookEvent} and its 16
 * subtypes are {@link io.jaiclaw.core.api.Experimental} for the
 * 0.8.0 cycle: the broad shape is settled but field-level details
 * (e.g. which optional fields land on
 * {@link io.jaiclaw.core.hook.event.SessionEndedEvent}) may evolve
 * before the program graduates to {@link io.jaiclaw.core.api.Stable}
 * in 0.9.
 */
@org.jspecify.annotations.NullMarked
package io.jaiclaw.core.hook.event;
