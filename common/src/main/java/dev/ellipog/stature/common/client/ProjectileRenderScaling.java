package dev.ellipog.stature.common.client;

import dev.ellipog.stature.common.StatureProjectile;

import org.jetbrains.annotations.Nullable;

/**
 * Decides the drawn size of a projectile from the same snapshot its hitbox uses.
 *
 * <p>Unscaled means exactly 1.0, not "1.0 after a multiply", so the vanilla draw path is left
 * untouched. A corrupt factor is treated as no factor: a NaN would turn the pose matrix into
 * nothing drawable, and the hitbox mixin already ignores the same value.
 */
public final class ProjectileRenderScaling {

    private ProjectileRenderScaling() {
    }

    /** The draw factor for one projectile, or exactly 1.0 when it has no usable inherited size. */
    public static float factor(@Nullable StatureProjectile projectile) {
        if (projectile == null || !projectile.stature$projectileScaled()) {
            return 1.0F;
        }
        float factor = projectile.stature$projectileScale();
        return Float.isFinite(factor) && factor > 0.0F ? factor : 1.0F;
    }
}
