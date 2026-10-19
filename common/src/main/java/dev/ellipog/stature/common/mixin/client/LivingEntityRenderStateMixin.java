package dev.ellipog.stature.common.mixin.client;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.client.StatureRenderState;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Makes room on the render state for three factors where vanilla has one, plus the previous tick's
 * sample so a frame can blend instead of step.
 *
 * <p>{@code LivingEntityRenderState} carries a single {@code public float scale}, set from
 * {@code entity.getScale()} in {@code extractRenderState}. Per-axis drawing needs the shape to
 * survive the trip from the entity to {@code submit}, and smooth drawing needs the sample from the
 * tick before as well: this is that room.
 *
 * <p>Lazily defaulted rather than initialised, for the same reason the entity's own field is: a
 * unique field with an initialiser is merged into constructors, and a render state is built by
 * vanilla's own pool before anything of Stature's has run. An unpushed state therefore draws
 * exactly vanilla.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements StatureRenderState {

    @Unique
    private boolean stature$hasHistory;

    @Unique
    private int stature$lastTick;

    @Unique
    private float stature$partialTick;

    @Unique
    private float stature$prevScale;

    @Unique
    private float stature$scale;

    @Unique
    private ScaleAxes stature$prevShape;

    @Unique
    private ScaleAxes stature$shape;

    @Override
    public void stature$pushFrame(int tick, float partialTick, float scale, ScaleAxes shape) {
        if (!this.stature$hasHistory || tick < this.stature$lastTick) {
            // First sample, or a pooled state reused by a younger entity: there is no earlier
            // sample to blend from, so history starts here and this frame draws exact.
            this.stature$hasHistory = true;
            this.stature$prevScale = scale;
            this.stature$prevShape = shape;
        } else if (tick != this.stature$lastTick) {
            this.stature$prevScale = this.stature$scale;
            this.stature$prevShape = this.stature$shape;
        }
        this.stature$scale = scale;
        this.stature$shape = shape;
        this.stature$lastTick = tick;
        this.stature$partialTick = partialTick;
    }

    @Override
    public float stature$renderScale() {
        if (!this.stature$hasHistory) {
            return 1.0F;
        }
        float t = stature$unit(this.stature$partialTick);
        return this.stature$prevScale + (this.stature$scale - this.stature$prevScale) * t;
    }

    @Override
    public ScaleAxes stature$renderShape() {
        if (!this.stature$hasHistory || this.stature$prevShape == null || this.stature$shape == null) {
            return ScaleAxes.UNIFORM;
        }
        return ScaleAxes.lerp(this.stature$prevShape, this.stature$shape, stature$unit(this.stature$partialTick));
    }

    /**
     * Into {@code [0, 1]}, written so a NaN becomes 0 rather than surviving: the {@code Math.min} /
     * {@code Math.max} pair passes NaN straight through, and a NaN blend factor would paint the
     * entity nowhere.
     */
    @Unique
    private static float stature$unit(float t) {
        if (!(t >= 0.0F)) {
            return 0.0F;
        }
        return t > 1.0F ? 1.0F : t;
    }
}
