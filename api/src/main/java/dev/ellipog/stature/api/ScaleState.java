package dev.ellipog.stature.api;

/**
 * Everything about one entity's size that Stature owns: the uniform scale and the box's shape.
 *
 * <p>Two numbers' worth of state, and they mean different things. {@code scale} is the game's own
 * attribute -- it drives pathfinding, collision, camera height and the client's own box -- and
 * {@code axes} is the shaping on top of it, which only Stature applies. Keeping them apart is what
 * lets "make it twice as big" and "make it twice as deep" be different requests.
 *
 * <p>Game-free and immutable, so a transition can hold two of them and a test can compare them.
 */
public record ScaleState(float scale, ScaleAxes axes) {

    /** Vanilla: a scale of one and a square footprint. */
    public static final ScaleState UNIFORM = new ScaleState(1.0F, ScaleAxes.UNIFORM);

    public ScaleState {
        if (!ScaleLimits.within(scale)) {
            throw new IllegalArgumentException("Stature: scale must be within "
                    + ScaleLimits.MIN + ".." + ScaleLimits.MAX + ", got " + scale);
        }
        java.util.Objects.requireNonNull(axes, "axes");
    }

    /** A scale with a square footprint. */
    public static ScaleState of(float scale) {
        return new ScaleState(scale, ScaleAxes.UNIFORM);
    }

    /** This state with a different uniform scale, keeping the shape. */
    public ScaleState withScale(float newScale) {
        return new ScaleState(newScale, this.axes);
    }

    /** This state with a different shape, keeping the uniform scale. */
    public ScaleState withAxes(ScaleAxes newAxes) {
        return new ScaleState(this.scale, newAxes);
    }

    /** Whether this is vanilla in every respect. */
    public boolean isUniform() {
        return this.scale == 1.0F && this.axes.isUniform();
    }

    /** Point on the line from {@code from} to {@code to} at {@code t}, easing applied by the caller. */
    public static ScaleState lerp(ScaleState from, ScaleState to, double t) {
        return new ScaleState(
                // Clamped, because a curve may overshoot: elastic passes its target on the way in,
                // and a target at the top of the envelope would otherwise ask for 16.1 and throw
                // from the constructor mid-animation.
                ScaleLimits.clamp((float) (from.scale + (to.scale - from.scale) * t)),
                ScaleAxes.lerp(from.axes, to.axes, t));
    }
}
