package dev.ellipog.stature.common.mixin;

import dev.ellipog.stature.common.channel.ChannelService;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Scales hunger drain by the saturation channel (plan §14.9's odd one out).
 *
 * <p>Every exhaustion in the game funnels through {@code causeFoodExhaustion} -- sprinting,
 * jumping, attacking, swimming -- so one argument tweak covers all of them, and the food tick
 * itself is untouched. The factor is 1.0 unless the channel is overridden, which keeps this a
 * no-op for every entity that never heard of the channel.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @ModifyArg(
            method = "causeFoodExhaustion(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"),
            index = 0)
    private float stature$scaleExhaustion(float amount) {
        float factor = ChannelService.exhaustionMultiplier((LivingEntity) (Object) this);
        return factor == 1.0F ? amount : amount * factor;
    }
}
