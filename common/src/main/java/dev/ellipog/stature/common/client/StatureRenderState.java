package dev.ellipog.stature.common.client;

import dev.ellipog.stature.api.ScaleAxes;

/**
 * What a render state can be asked about the shape of the thing it is about to draw.
 *
 * <p>26.3 draws a state, not an entity: {@code extractRenderState} fills one and {@code submit}
 * draws it, and {@code submit} never sees the entity. {@code LivingEntityRenderState} carries a
 * single {@code scale} float and no room for three, so the shape is copied onto the state on the way
 * in and read back on the way out.
 *
 * <p>The game ticks at 20 Hz but draws at display rate, so a value copied once per tick would step
 * visibly mid-ease. Each frame therefore pushes the entity's current tick, scale and shape, and the
 * state answers with the blend between the last two ticks at the frame's partial tick: the hitbox
 * stays exactly on the server's numbers while the picture glides between them, which is the same
 * split vanilla itself uses for positions.
 *
 * <p>Implemented by a mixin, hence the {@code stature$} prefix. This interface names no client class
 * itself, so it is safe to load on a dedicated server even though only a client ever uses it.
 */
public interface StatureRenderState {

    /**
     * Records one frame's sample. Called from {@code extractRenderState}, which always runs before
     * {@code submit} for the same frame.
     */
    void stature$pushFrame(int tick, float partialTick, float scale, ScaleAxes shape);

    /** The uniform scale blended to this frame, or 1.0 when nothing has been pushed yet. */
    float stature$renderScale();

    /** The shape blended to this frame, or {@link ScaleAxes#UNIFORM} when nothing is pushed yet. */
    ScaleAxes stature$renderShape();
}
