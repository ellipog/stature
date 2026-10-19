package dev.ellipog.stature.spike.mixin.client;

import dev.ellipog.stature.spike.PerAxisDimensions;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Makes room on the render state for three factors where vanilla has one.
 *
 * <p>{@code LivingEntityRenderState} carries a single {@code public float scale} (line 20 of the 26.3
 * source), set from {@code entity.getScale()} in {@code extractRenderState}. Per-axis rendering needs
 * the width, height and depth factors to survive the trip from the entity to {@code submit}, and this
 * is that room.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements PerAxisDimensions.Render {

    @Unique
    private float stature$renderWidth = 1.0F;

    @Unique
    private float stature$renderHeight = 1.0F;

    @Unique
    private float stature$renderDepth = 1.0F;

    @Override
    public void stature$setRenderScale(float widthMultiplier, float heightMultiplier, float depthMultiplier) {
        this.stature$renderWidth = widthMultiplier;
        this.stature$renderHeight = heightMultiplier;
        this.stature$renderDepth = depthMultiplier;
    }

    @Override
    public float stature$renderWidth() {
        return this.stature$renderWidth;
    }

    @Override
    public float stature$renderHeight() {
        return this.stature$renderHeight;
    }

    @Override
    public float stature$renderDepth() {
        return this.stature$renderDepth;
    }
}
