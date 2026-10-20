package dev.ellipog.stature.common;

import dev.ellipog.stature.common.channel.ChannelTuning;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;

/**
 * Scales the radius of an explosion back to the entity that caused it.
 *
 * <p>The cause is resolved with vanilla's own rule: a creeper is the living source, a ghast's
 * fireball resolves through its owner, and TNT resolves through the player or entity that primed
 * it. That is deliberately the only attribution used: an explosion with no resolvable living cause
 * is left vanilla rather than guessed from proximity. A TNT block lit by redstone has no owner and
 * therefore does not scale.
 */
public final class ExplosionScaling {

    private ExplosionScaling() {
    }

    /** The radius multiplier for this explosion source, exactly 1.0 when nothing is attributable. */
    public static float factor(Entity source) {
        LivingEntity cause = Explosion.getIndirectSourceEntity(source);
        if (cause == null || !StatureConfig.enabled(cause, StatureConfig.EXPLOSION_SCALE)) {
            return 1.0F;
        }
        return ChannelTuning.factor(cause, StatureConfig.EXPLOSION_SCALE_CHANNEL);
    }

    /** The scaled radius, or the original when the channel is off. */
    public static float radius(Entity source, float radius) {
        float factor = factor(source);
        return factor == 1.0F ? radius : radius * factor;
    }
}
