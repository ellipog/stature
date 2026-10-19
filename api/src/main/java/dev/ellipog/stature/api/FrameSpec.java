package dev.ellipog.stature.api;

import java.util.Objects;

/**
 * A named box, as a datapack declares it (plan §14.8).
 *
 * <p>A frame is a <em>size in blocks</em> rather than a set of multipliers, because that is what a
 * pack author is actually deciding: "this kin is 0.55 wide and 1.71 tall". It also carries the scale
 * it wants to be seen at, so a frame can be a size and not only a shape, and the easing a change to
 * it should use.
 *
 * <p>Composition with the uniform scale is the plan's rule: a frame's size is absolute and a scale
 * multiplies it, so a 1.65x entity on a 0.99 x 2.97 frame is exactly 1.63 x 4.90 blocks. The frame
 * itself is converted to the multipliers the rest of Stature works in by
 * {@link #axesFor(float, float)}, against the entity's own base box -- which is why one frame can be
 * handed to a player (0.6 x 1.8), a zombie (0.6 x 1.95) or a modded creature and mean the same thing.
 *
 * <p>Game-free: this is a value, and the reading of it from a pack lives in {@code stature-common}.
 */
public record FrameSpec(float scale, float width, float height, float depth, float eyeHeight, Easing easing) {

    /** No eye-height override: the camera sits where the shaped dimensions put it. */
    public static final float NO_EYE_HEIGHT = -1.0F;

    /** What a frame eases with when it does not say. */
    public static final Easing DEFAULT_EASING = Easing.EASE_OUT_CUBIC;

    public FrameSpec {
        if (!ScaleLimits.within(scale)) {
            throw new IllegalArgumentException(
                    "scale must be within " + ScaleLimits.MIN + ".." + ScaleLimits.MAX + ", got " + scale);
        }
        positive(width, "width");
        positive(height, "height");
        positive(depth, "depth");
        if (eyeHeight != NO_EYE_HEIGHT && !(eyeHeight > 0.0F)) {
            throw new IllegalArgumentException("eye_height must be positive or absent, got " + eyeHeight);
        }
        Objects.requireNonNull(easing, "easing");
    }

    private static void positive(float value, String field) {
        if (!(value > 0.0F) || Float.isInfinite(value) || Float.isNaN(value)) {
            throw new IllegalArgumentException(field + " must be a positive finite number, got " + value);
        }
    }

    /** A frame at scale 1 with no eye-height override, easing with the default curve. */
    public static FrameSpec of(float width, float height, float depth) {
        return new FrameSpec(1.0F, width, height, depth, NO_EYE_HEIGHT, DEFAULT_EASING);
    }

    public boolean hasEyeHeight() {
        return this.eyeHeight != NO_EYE_HEIGHT;
    }

    /**
     * The multipliers that turn a base box of {@code baseWidth x baseHeight} into this frame's size.
     *
     * <p>Depth is measured against the base <em>width</em>, because vanilla's footprint is square: an
     * entity is as deep as it is wide unless something says otherwise, and this is that something.
     */
    public ScaleAxes axesFor(float baseWidth, float baseHeight) {
        positive(baseWidth, "base box width");
        positive(baseHeight, "base box height");
        return new ScaleAxes(this.width / baseWidth, this.height / baseHeight, this.depth / baseWidth);
    }

    /** This frame's size at {@code scale}, as the box the game will actually build. */
    public ScaleAxes boxAt(float scale) {
        return new ScaleAxes(this.width * scale, this.height * scale, this.depth * scale);
    }
}
