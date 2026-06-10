/**
 * API stability marker annotations used across the JaiClaw codebase.
 *
 * <p>Every public top-level type in JaiClaw carries exactly one of:
 *
 * <ul>
 *   <li>{@link io.jaiclaw.core.api.Stable} — committed-to; no
 *       breaking changes after 1.0</li>
 *   <li>{@link io.jaiclaw.core.api.Experimental} — public but
 *       evolving; may change at any release boundary</li>
 *   <li>{@link io.jaiclaw.core.api.Internal} — no compatibility
 *       guarantees</li>
 * </ul>
 *
 * <p>The stability program shipped in 0.8.0 P3.5 (audit
 * {@code docs/CODEBASE-ANALYSIS-2026-06-10.md} §3.5). Adopters
 * planning a 1.0 upgrade can grep for {@code @Stable} to know which
 * surfaces they can build against safely.
 */
@org.jspecify.annotations.NullMarked
package io.jaiclaw.core.api;
