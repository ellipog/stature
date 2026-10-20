package dev.ellipog.stature.common.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Pulls the near plane in for micro cameras.
 *
 * <p>The perspective's near plane is a hardcoded 0.05 blocks: with a 0.1-block eye height at
 * 0.0625×, walls between the eye and the plane clip out of view and the player sees through
 * them. Scaling the plane with the camera entity's own scale -- floored at 0.005 to protect
 * depth precision -- keeps the ratio of eye height to near plane roughly what it is at 1×.
 * Macro entities keep the vanilla plane: nothing beyond it changes.
 *
 * <p>Note on the plan's pointer: M3 names {@code GameRenderer ~683}, but in 26.3 that 0.05 is
 * the hand/HUD projection. The world projection's near plane is set in {@code Camera.update},
 * which is what this modifies.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private @Nullable Entity entity;

    @ModifyArg(
            method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"),
            index = 0)
    private float stature$shrinkNearPlane(float zNear) {
        Entity cameraEntity = this.entity;
        if (cameraEntity instanceof LivingEntity living) {
            float scale = living.getScale();
            if (Float.isFinite(scale) && scale > 0.0F && scale < 1.0F) {
                return Math.max(0.005F, 0.05F * scale);
            }
        }
        return zNear;
    }
}
