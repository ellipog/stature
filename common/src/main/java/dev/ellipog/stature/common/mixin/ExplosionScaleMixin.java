package dev.ellipog.stature.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.ellipog.stature.common.ExplosionScaling;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Grows or shrinks an explosion's radius at the one server-side funnel.
 *
 * <p>{@code ServerLevel.explode} constructs a {@code ServerExplosion} with the resolved source, so
 * wrapping that constructor is the only place where a creeper, a ghast's fireball and primed TNT
 * all become the same question without guessing from proximity. The packet sent to clients uses
 * this same radius argument, so the particles and sound follow the server's answer, and the
 * explosion channel owns the radius while the attacker still takes the normal damage-source route.
 */
@Mixin(ServerLevel.class)
public abstract class ExplosionScaleMixin {

    @WrapOperation(
            method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at = @At(value = "NEW", target = "Lnet/minecraft/world/level/ServerExplosion;"))
    private ServerExplosion stature$scaleExplosion(
            ServerLevel level,
            Entity source,
            DamageSource damageSource,
            ExplosionDamageCalculator calculator,
            Vec3 center,
            float radius,
            boolean fire,
            Explosion.BlockInteraction interaction,
            Operation<ServerExplosion> original) {
        return original.call(level, source, damageSource, calculator, center,
                ExplosionScaling.radius(source, radius), fire, interaction);
    }
}
