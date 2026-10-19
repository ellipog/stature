package dev.ellipog.stature.api;

/**
 * The shape of a box, as three multipliers of the entity's own dimensions (plan §14.2).
 *
 * <p>Vanilla gives an entity one width, used for both X and Z, so a non-uniform frame needs a third
 * number vanilla has no field for. These are <em>multipliers on top of</em> whatever the game
 * already composed (type dimensions, then age, then {@link ScaleState}'s uniform scale): {@code 1, 1,
 * 1} is exactly vanilla, and {@code 0.55 / 0.6, 1.0, 2.0} is narrower than tall and twice as deep as
 * it is wide.
 *
 * <p>Game-free, like everything else in this module: it is a value, and the mixins that apply it
 * live in {@code stature-common}.
 */
public record ScaleAxes(float width, float height, float depth) {

    /** No shaping at all: the entity is exactly as wide as it is deep. */
    public static final ScaleAxes UNIFORM = new ScaleAxes(1.0F, 1.0F, 1.0F);

    /**
     * Every multiplier must be a positive, finite number.
     *
     * <p>A zero or negative extent is not a small entity, it is a box that inverts its own
     * comparison and a matrix that cannot be inverted, and a NaN reaches the renderer as invisible
     * geometry rather than as an error. Rejected here rather than clamped, because a caller passing
     * one has a bug and silently reading {@code 1.0} would hide it.
     */
    public ScaleAxes {
        requirePositive(width, "width");
        requirePositive(height, "height");
        requirePositive(depth, "depth");
    }

    private static void requirePositive(float value, String axis) {
        if (!(value > 0.0F) || Float.isInfinite(value) || Float.isNaN(value)) {
            throw new IllegalArgumentException("Stature: " + axis + " multiplier must be a positive finite number, got " + value);
        }
    }

    /** Whether this is exactly vanilla's square footprint. */
    public boolean isUniform() {
        return this.width == 1.0F && this.height == 1.0F && this.depth == 1.0F;
    }

    /** This applied to a box {@code width x height x depth}, in blocks. */
    public float applyWidth(float baseWidth) {
        return baseWidth * this.width;
    }

    /** This applied to a height, in blocks. */
    public float applyHeight(float baseHeight) {
        return baseHeight * this.height;
    }

    /** This applied to a width to get the Z extent, in blocks. */
    public float applyDepth(float baseWidth) {
        return baseWidth * this.depth;
    }

    /**
     * What to multiply an already-shaped width by, to get the Z extent.
     *
     * <p>The box is built from {@code EntityDimensions}, whose width has already had
     * {@link #width()} applied to it, so the box site cannot multiply by {@link #depth()} directly:
     * it needs the ratio, and this is that ratio in one place rather than a division at every use.
     */
    public float zRatio() {
        return this.depth / this.width;
    }

    /** Point on the line from {@code from} to {@code to} at {@code t}, per component. */
    public static ScaleAxes lerp(ScaleAxes from, ScaleAxes to, double t) {
        return new ScaleAxes(
                (float) (from.width + (to.width - from.width) * t),
                (float) (from.height + (to.height - from.height) * t),
                (float) (from.depth + (to.depth - from.depth) * t));
    }
}
