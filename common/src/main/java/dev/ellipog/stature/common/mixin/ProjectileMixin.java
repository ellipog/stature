package dev.ellipog.stature.common.mixin;

import java.util.Optional;

import dev.ellipog.stature.common.ProjectileScaling;
import dev.ellipog.stature.common.StatureProjectile;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.parts.HitRegion;
import dev.ellipog.stature.common.parts.RegionService;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stashes hurt regions for projectile hits.
 *
 * <p>This is the one dispatcher every projectile funnels through, and it still holds the hit
 * location: downstream the subclasses keep only the entity. The stash is spent moments later
 * at damage time, in the same tick, so a location that never becomes damage evaporates on its
 * own. Client-side hits stash nothing; the client never applies damage.
 *
 * <p>Two projectiles that hit the same victim in one tick both write the same field: the last
 * hit wins. That is deliberate and documented rather than a queue, because damage is applied
 * once per hit in the same tick and the alternative is a per-tick list for a case vanilla
 * already resolves by ignoring the extra hit's cooldown.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin implements StatureProjectile {

    @Unique
    private float stature$projectileScale = 1.0F;

    @Unique
    private boolean stature$projectileScaled;

    @Unique
    private boolean stature$projectileSpeedApplied;

    @Override
    public float stature$projectileScale() {
        return this.stature$projectileScale;
    }

    @Override
    public boolean stature$projectileScaled() {
        return this.stature$projectileScaled;
    }

    @Override
    public void stature$setProjectileScale(float factor) {
        this.stature$projectileScale = factor;
        this.stature$projectileScaled = true;
    }

    @Override
    public boolean stature$projectileSpeedApplied() {
        return this.stature$projectileSpeedApplied;
    }

    @Override
    public void stature$markProjectileSpeedApplied() {
        this.stature$projectileSpeedApplied = true;
    }

    @Inject(method = "setOwner(Lnet/minecraft/world/entity/EntityReference;)V", at = @At("TAIL"))
    private void stature$inheritOwnerScale(CallbackInfo info) {
        ProjectileScaling.ownerSet((Projectile) (Object) this);
    }

    @Inject(method = "shoot(DDDFF)V", at = @At("TAIL"))
    private void stature$inheritLaunchSpeed(CallbackInfo info) {
        ProjectileScaling.shot((Projectile) (Object) this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void stature$saveProjectileScale(ValueOutput output, CallbackInfo info) {
        ProjectileScaling.save((Projectile) (Object) this, output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void stature$loadProjectileScale(ValueInput input, CallbackInfo info) {
        ProjectileScaling.load((Projectile) (Object) this, input);
    }

    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At("HEAD"))
    private void stature$stashRegion(HitResult hitResult, CallbackInfo info) {
        Projectile self = (Projectile) (Object) this;
        if (self.level().isClientSide() || hitResult.getType() != HitResult.Type.ENTITY) {
            return;
        }
        EntityHitResult entityHit = (EntityHitResult) hitResult;
        if (entityHit.getEntity() instanceof LivingEntity victim) {
            Optional<HitRegion> region = RegionService.resolveHit(victim, entityHit.getLocation());
            region.ifPresent(hit -> ((StatureScale) victim).stature$setIncomingRegion(hit));
            ((StatureScale) victim).stature$setIncomingProjectileDamage(
                    ProjectileScaling.hitDamageFactor(self), victim.tickCount);
        }
    }
}
