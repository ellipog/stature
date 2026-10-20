package dev.ellipog.stature.common;

import dev.ellipog.stature.common.channel.ChannelTuning;
import dev.ellipog.stature.common.net.StaturePackets;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Gives a projectile the size, hitbox, damage and speed of the entity that fired it.
 *
 * <p>The trigger is {@code Projectile.setOwner}: every vanilla projectile either sets its owner in
 * its constructor or receives one when its spawn packet is recreated, so one hook covers arrows,
 * tridents, fireballs, potions, snowballs, spit and modded subclasses without naming any of them.
 * The factor is a snapshot, and the velocity is multiplied exactly once: a projectile whose owner
 * changes (a deflected fireball, a returned trident) keeps the size and speed it was fired with.
 */
public final class ProjectileScaling {

    private static final String KEY_FACTOR = "stature_projectile_scale";

    private static final String KEY_SPEED_APPLIED = "stature_projectile_speed";

    private ProjectileScaling() {
    }

    /** Called after a projectile learns its owner, on either side. */
    public static void ownerSet(Projectile projectile) {
        StatureProjectile duck = (StatureProjectile) projectile;
        if (duck.stature$projectileScaled()) {
            return;
        }
        if (!(projectile.getOwner() instanceof LivingEntity owner)) {
            return;
        }
        float factor = ChannelTuning.factor(owner, StatureConfig.PROJECTILE_SCALE_CHANNEL);
        duck.stature$setProjectileScale(factor);
        projectile.refreshDimensions();
        if (!projectile.level().isClientSide()) {
            // The spawn packet's velocity is already the scaled one, so multiplying it again here
            // would give the client a different flight from the server's. The size, on the other
            // hand, is needed on both sides immediately; the explicit sync below corrects a client
            // that could not derive it.
            applySpeed(projectile, duck);
            StaturePackets.broadcastProjectileScale(projectile, factor);
        }
    }

    /** Called after {@code Projectile.shoot}, where the initial movement is finally known. */
    public static void shot(Projectile projectile) {
        StatureProjectile duck = (StatureProjectile) projectile;
        if (!projectile.level().isClientSide() && duck.stature$projectileScaled()) {
            applySpeed(projectile, duck);
        }
    }

    /** Saves the inherited factor, so a projectile in an unloaded chunk does not grow back. */
    public static void save(Projectile projectile, ValueOutput output) {
        StatureProjectile duck = (StatureProjectile) projectile;
        if (!duck.stature$projectileScaled() || duck.stature$projectileScale() == 1.0F) {
            return;
        }
        output.putFloat(KEY_FACTOR, duck.stature$projectileScale());
        if (duck.stature$projectileSpeedApplied()) {
            output.putBoolean(KEY_SPEED_APPLIED, true);
        }
    }

    /** Restores a saved factor after the owner has been read back. */
    public static void load(Projectile projectile, ValueInput input) {
        StatureProjectile duck = (StatureProjectile) projectile;
        float saved = input.getFloatOr(KEY_FACTOR, 1.0F);
        if (!(saved > 0.0F) || !Float.isFinite(saved)) {
            return;
        }
        if (saved == 1.0F && !input.getBooleanOr(KEY_SPEED_APPLIED, false)) {
            return;
        }
        duck.stature$setProjectileScale(saved);
        if (input.getBooleanOr(KEY_SPEED_APPLIED, false)) {
            duck.stature$markProjectileSpeedApplied();
        }
        projectile.refreshDimensions();
        if (!projectile.level().isClientSide()) {
            // A chunk load usually happens before tracking, so this often reaches nobody. It is
            // still the correct send for a projectile restored into a level that already has
            // viewers, and it costs one call on the load path.
            StaturePackets.broadcastProjectileScale(projectile, saved);
        }
    }

    /**
     * The damage multiplier a direct projectile hit carries.
     *
     * <p>The projectile mixin stashes this on the victim when the hit dispatches, and the hurt
     * funnel spends it in the same tick. Explosion damage never goes through a projectile's
     * {@code onHit}, so the explosion channel owns that radius without this multiplying it twice.
     */
    public static float hitDamageFactor(Projectile projectile) {
        if (projectile instanceof StatureProjectile duck && duck.stature$projectileScaled()) {
            return ChannelTuning.projectileDamageFactor(duck.stature$projectileScale());
        }
        return 1.0F;
    }

    private static void applySpeed(Projectile projectile, StatureProjectile duck) {
        if (duck.stature$projectileSpeedApplied() || duck.stature$projectileScale() == 1.0F) {
            return;
        }
        Vec3 movement = projectile.getDeltaMovement();
        if (movement.lengthSqr() < 1.0E-10) {
            return;
        }
        projectile.setDeltaMovement(
                movement.scale(ChannelTuning.projectileSpeedFactor(duck.stature$projectileScale())));
        duck.stature$markProjectileSpeedApplied();
    }
}
