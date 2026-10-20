package dev.ellipog.stature.common.net;

/**
 * How far a macro footstep packet should travel (improvement plan M5).
 *
 * <p>Vanilla's {@code SoundEvent.getRange(v)} gives 16 blocks at a volume of one and 16*v above
 * it, so the audibility of a step is already a function of its volume. The packet radius follows
 * the same rule so the shake is sent exactly where the sound can be heard: at least 16 blocks, at
 * most 64. The old code sent it to the whole dimension; the cap is what keeps a 16x step from
 * turning into a 256-block broadcast.
 *
 * <p>Pure math so a gametest does not need two clients to check the formula.
 */
public final class FootstepScope {

    public static final double MIN_RADIUS = 16.0;

    public static final double MAX_RADIUS = 64.0;

    private FootstepScope() {
    }

    /** The packet radius for a heard volume, clamped to the range a step can usefully travel. */
    public static double radius(float volume) {
        if (!Float.isFinite(volume) || volume <= 0.0F) {
            return MIN_RADIUS;
        }
        return Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, 16.0 * volume));
    }
}
