package dev.ellipog.stature.common.mixin.client;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.client.StatureRenderState;

import java.util.Map;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Makes room on the render state for three factors where vanilla has one.
 *
 * <p>{@code LivingEntityRenderState} carries a single {@code public float scale}, set from
 * {@code entity.getScale()} in {@code extractRenderState}. Per-axis drawing needs the shape to
 * survive the trip from the entity to {@code submit}, and smooth drawing needs the blended
 * sample for this frame: this is that room. The blend itself lives on the entity
 * ({@code RenderSampler}) because 26.3 builds a fresh state every frame -- history kept here
 * would be written and dropped without ever being read twice.
 *
 * <p>Lazily defaulted rather than initialised, for the same reason the entity's own field is: a
 * unique field with an initialiser is merged into constructors, and a render state is built by
 * vanilla's own pool before anything of Stature's has run. An unpushed state therefore draws
 * exactly vanilla.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements StatureRenderState {

    @Unique
    private float stature$visualScale = 1.0F;

    @Unique
    private float stature$attributeScale = 1.0F;

    @Unique
    private ScaleAxes stature$visualShape;

    @Unique
    private Map<String, Float> stature$channels;

    @Unique
    private Map<String, ScaleAxes> stature$parts;

    @Override
    public void stature$pushVisual(float visualScale, float attributeScale, ScaleAxes shape) {
        this.stature$visualScale = visualScale;
        this.stature$attributeScale = attributeScale;
        this.stature$visualShape = shape;
    }

    @Override
    public float stature$visualScale() {
        return this.stature$visualScale;
    }

    @Override
    public float stature$attributeScale() {
        return this.stature$attributeScale;
    }

    @Override
    public ScaleAxes stature$visualShape() {
        return this.stature$visualShape == null ? ScaleAxes.UNIFORM : this.stature$visualShape;
    }

    @Override
    public void stature$pushChannels(Map<String, Float> channels) {
        this.stature$channels = Map.copyOf(channels);
    }

    @Override
    public boolean stature$hidden() {
        Float visibility = this.stature$channels().get("stature:visibility");
        return visibility != null && visibility < 0.5F;
    }

    @Override
    public float stature$heldFactor() {
        return stature$factor("stature:held_item");
    }

    @Override
    public float stature$shadowFactor() {
        return stature$factor("stature:shadow");
    }

    @Override
    public void stature$pushParts(Map<String, ScaleAxes> parts) {
        this.stature$parts = Map.copyOf(parts);
    }

    @Override
    public Map<String, ScaleAxes> stature$partFactors() {
        return this.stature$parts == null ? Map.of() : this.stature$parts;
    }

    /** This frame's overrides, or nothing when no frame has pushed yet. */
    @Unique
    private Map<String, Float> stature$channels() {
        return this.stature$channels == null ? Map.of() : this.stature$channels;
    }

    /**
     * One render factor: the override, or exactly 1 when unset or corrupt. A NaN here would scale
     * a matrix by nothing-meaningful, so corrupt values degrade to neutral like everywhere else.
     */
    @Unique
    private float stature$factor(String id) {
        Float value = this.stature$channels().get(id);
        return value != null && value >= 0.0F ? value : 1.0F;
    }
}
