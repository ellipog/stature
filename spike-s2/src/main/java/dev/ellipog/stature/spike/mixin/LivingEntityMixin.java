package dev.ellipog.stature.spike.mixin;

import dev.ellipog.stature.spike.PerAxisDimensions;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Width and height, independently, at the one place vanilla composes them.
 *
 * <p>26.3 already has the two-factor form, {@code EntityDimensions.scale(float widthFactor, float
 * heightFactor)}, and {@code LivingEntity.getDimensions(Pose)} uses the one-factor form:
 *
 * <pre>
 * public final EntityDimensions getDimensions(Pose pose) {
 *     return pose == Pose.SLEEPING ? SLEEPING_DIMENSIONS
 *                                  : this.getDefaultDimensions(pose).scale(this.getScale());
 * }
 * </pre>
 *
 * <p>It is {@code final}, which is why this is a mixin rather than an override, and why the spike
 * exists at all: the data model was never the obstacle.
 *
 * <p>Injecting at RETURN rather than overwriting the method means the multiplication happens once
 * per call, on top of whatever vanilla composed (type dimensions, then age, then the attribute),
 * and never accumulates, because {@code getDimensions} recomputes from those each time. A {@code
 * fixed} dimension ignores it for free: {@code scale} returns {@code this} when {@code fixed} is
 * true, which covers sleeping and dying players.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements PerAxisDimensions.Size {

    @Unique
    private float stature$widthMultiplier = 1.0F;

    @Unique
    private float stature$heightMultiplier = 1.0F;

    @Override
    public float stature$widthMultiplier() {
        return this.stature$widthMultiplier;
    }

    @Override
    public float stature$heightMultiplier() {
        return this.stature$heightMultiplier;
    }

    @Override
    public void stature$setSize(float widthMultiplier, float heightMultiplier) {
        this.stature$widthMultiplier = widthMultiplier;
        this.stature$heightMultiplier = heightMultiplier;
        ((LivingEntity) (Object) this).refreshDimensions();
    }

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void stature$perAxisDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (this.stature$widthMultiplier != 1.0F || this.stature$heightMultiplier != 1.0F) {
            cir.setReturnValue(
                    cir.getReturnValue().scale(this.stature$widthMultiplier, this.stature$heightMultiplier));
        }
    }
}
