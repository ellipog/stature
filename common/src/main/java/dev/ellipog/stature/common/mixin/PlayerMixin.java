package dev.ellipog.stature.common.mixin;

import java.util.Optional;

import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.parts.HitRegion;
import dev.ellipog.stature.common.parts.RegionService;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    /**
     * Stashes hurt regions for melee hits.
     *
     * <p>Vanilla's attack packet names whom, never where, so the location is re-derived: a
     * view-vector raycast must land on the same victim within reach, or there is no stash and
     * damage stays vanilla. Blocks clip the ray, so a wall between the swings degrades to a
     * normal hit rather than a rewarded one. Sweep victims never pass through here.
     */
    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void stature$stashMeleeRegion(Entity target, CallbackInfo info) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide() || !(target instanceof LivingEntity victim)) {
            return;
        }
        AttributeInstance reach = self.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        double distance = (reach == null ? 4.5 : reach.getValue()) + 1.0;
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(self, entity -> entity == target, distance);
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == target) {
            Optional<HitRegion> region = RegionService.resolveHit(victim, entityHit.getLocation());
            region.ifPresent(hitRegion -> ((StatureScale) victim).stature$setIncomingRegion(hitRegion));
        }
    }
}
