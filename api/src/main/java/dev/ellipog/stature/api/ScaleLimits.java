package dev.ellipog.stature.api;

/**
 * The envelope a scale value may live in.
 *
 * <p>Not a policy Stature invented: {@code scale} is a {@code RangedAttribute} in 26.3 with a
 * default of 1.0 and hard bounds of 0.0625 and 16.0, and the game clamps every write to it. A
 * number outside this envelope is not "an unusual scale", it is a number the attribute will not
 * store -- so anything below {@link #MIN} has to be Stature's own multiplier rather than the
 * game's scale (plan §14.5).
 */
public final class ScaleLimits {

    /** The smallest scale the game's own attribute accepts. */
    public static final float MIN = 0.0625F;

    /** The largest scale the game's own attribute accepts. */
    public static final float MAX = 16.0F;

    /** What a scale change eases over when the caller does not say (one second). */
    public static final int DEFAULT_TICKS = 20;

    private ScaleLimits() {
    }

    /** Whether {@code scale} is a value the game will store. */
    public static boolean within(float scale) {
        return scale >= MIN && scale <= MAX;
    }

    /**
     * {@code scale}, pulled into the envelope if it is outside it.
     *
     * <p>Total: unlike {@code Math.min}/{@code Math.max} alone, this also settles the non-finite
     * cases, because a corrupt packet can carry a NaN and the one place it must never reach is a
     * record constructor that throws on the network thread. NaN becomes exactly 1.0 -- no change --
     * which is the only honest reading of a number that is not a number.
     */
    public static float clamp(float scale) {
        if (Float.isNaN(scale)) {
            return 1.0F;
        }
        return Math.min(MAX, Math.max(MIN, scale));
    }
}
