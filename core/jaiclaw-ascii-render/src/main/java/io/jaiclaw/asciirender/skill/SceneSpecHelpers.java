package io.jaiclaw.asciirender.skill;

import io.jaiclaw.asciirender.profile.AsciiRenderProfiles;

/**
 * Profile-aware layout helpers for {@link RenderableTemplate} implementations.
 *
 * <p>Templates that want to size their output to the deployment's active
 * {@link io.jaiclaw.asciirender.profile.AsciiRenderProfile} call
 * {@link #activeWidth(int, int)} to get a width clamped to the template's
 * own {@code [min, max]} tolerances. This means adopters can flip
 * {@code jaiclaw.ascii.default-profile: telegram_mobile} and every
 * registered template's layout re-routes with no code change.
 *
 * <p>Lifted from the {@code jaiclaw-event-agent} reference app so future
 * apps don't re-invent the same clamp.
 */
public final class SceneSpecHelpers {

    private SceneSpecHelpers() {}

    /**
     * Return the width to build a scene for, clamped to {@code [min, max]}
     * and driven by the active {@link AsciiRenderProfiles#defaultProfile()}.
     *
     * <p>If the profile's width falls inside the template's tolerances, use
     * it as-is. If narrower than {@code min}, clamp up (a template's minimum
     * is load-bearing — narrower layouts collapse). If wider than {@code max},
     * clamp down (a template's maximum reflects design intent — the card
     * shouldn't sprawl on a wide shell profile).
     *
     * @param min minimum width the template can lay out at (inclusive)
     * @param max maximum width the template targets (inclusive)
     * @return effective width in [min, max]
     * @throws IllegalArgumentException if {@code min > max} or {@code min <= 0}
     */
    public static int activeWidth(int min, int max) {
        if (min <= 0) {
            throw new IllegalArgumentException("min must be positive, got: " + min);
        }
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
        }
        int profileWidth = AsciiRenderProfiles.defaultProfile().width();
        return Math.max(min, Math.min(max, profileWidth));
    }
}
