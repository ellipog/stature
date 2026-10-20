package dev.ellipog.stature.common.client;

import dev.ellipog.stature.api.ScaleAxes;

/**
 * Blends the last two client ticks into whatever partial tick this frame is on.
 *
 * <p>26.3 builds a fresh {@code EntityRenderState} every frame, so history kept on the state
 * itself never survives to the next frame and any ease steps at 20 Hz. History therefore lives
 * here, on the client entity: the client tick writes the current visual scale and shape, and
 * {@code extractRenderState} reads the blend for its partial tick. The hitbox never sees any
 * of this; it stays on the server's whole-tick numbers while the picture glides between them.
 *
 * <p>Pure math, no game classes: the interpolation is unit-testable headlessly, which is also
 * why the partial tick is clamped into {@code [0, 1]} the same way the old state blend did --
 * a NaN must degrade to the previous sample rather than paint the entity nowhere.
 */
public final class RenderSampler {

    private float prevScale = 1.0F;
    private float curScale = 1.0F;
    private ScaleAxes prevAxes = ScaleAxes.UNIFORM;
    private ScaleAxes curAxes = ScaleAxes.UNIFORM;

    /** Records this tick's visual scale and shape, shifting the previous sample back. */
    public void tick(float scale, ScaleAxes axes) {
        this.prevScale = this.curScale;
        this.prevAxes = this.curAxes;
        this.curScale = scale;
        this.curAxes = axes;
    }

    /** The uniform scale blended to this frame's partial tick. */
    public float scale(float partialTick) {
        float t = unit(partialTick);
        return this.prevScale + (this.curScale - this.prevScale) * t;
    }

    /** The shape blended to this frame's partial tick. */
    public ScaleAxes axes(float partialTick) {
        return ScaleAxes.lerp(this.prevAxes, this.curAxes, unit(partialTick));
    }

    private static float unit(float t) {
        if (!(t >= 0.0F)) {
            return 0.0F;
        }
        return t > 1.0F ? 1.0F : t;
    }
}
