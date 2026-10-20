package dev.ellipog.stature.common.mixin;

import dev.ellipog.stature.common.RidingOffset;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Lets the mount's {@code riding_offset} channel choose the seat scale.
 *
 * <p>Vanilla's {@code getPassengerAttachmentPoint} takes a scale, and a living mount already passes
 * its own scale there. The horse, for example, uses it to lift a standing rider. The channel makes
 * that number tunable: a giant riding a normal-sized horse can set the horse's
 * {@code stature:riding_offset} higher and stop clipping through it. With no override the ratio is
 * one, so an ordinary mount calls the same method with exactly the same argument.
 */
@Mixin(LivingEntity.class)
public abstract class RidingOffsetMixin {

    @ModifyArg(
            method = "getPassengerRidingPosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getPassengerAttachmentPoint(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityDimensions;F)Lnet/minecraft/world/phys/Vec3;"),
            index = 2)
    private float stature$seatScale(float scale) {
        return RidingOffset.seatScale((LivingEntity) (Object) this, scale);
    }
}
