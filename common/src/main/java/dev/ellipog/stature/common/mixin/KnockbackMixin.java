package dev.ellipog.stature.common.mixin;

import dev.ellipog.stature.common.channel.ChannelService;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales knockback both ways (plan §14.9's pair).
 *
 * <p>Both hooks sit at the two funnels vanilla already owns, so enchantments, sprint bonuses and
 * the knockback-resistance attribute all compose underneath: the received factor multiplies the
 * incoming power before resistance is even read, and the dealt factor multiplies the outgoing
 * result after enchantments. Neither hook can produce a NaN -- the factors degrade to neutral --
 * because a poisoned delta movement teleports rather than shoves.
 */
@Mixin(LivingEntity.class)
public abstract class KnockbackMixin {

    @ModifyVariable(
            method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private double stature$scaleReceivedKnockback(double power) {
        return power * ChannelService.knockbackReceivedFactor((LivingEntity) (Object) this);
    }

    @Inject(
            method = "getKnockback(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)F",
            at = @At("RETURN"),
            cancellable = true)
    private void stature$scaleDealtKnockback(
            Entity target, DamageSource damageSource, CallbackInfoReturnable<Float> cir) {
        float factor = ChannelService.knockbackDealtFactor((LivingEntity) (Object) this);
        if (factor != 1.0F) {
            cir.setReturnValue(cir.getReturnValue() * factor);
        }
    }
}
