package dev.ellipog.stature.common.mixin;

import java.util.Optional;

import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.parts.HitRegion;
import dev.ellipog.stature.common.parts.RegionService;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
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
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {

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
        }
    }
}
