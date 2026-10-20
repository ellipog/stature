package dev.ellipog.stature.common.mixin.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ellipog.stature.common.StatureProjectile;
import dev.ellipog.stature.common.client.ProjectileRenderScaling;
import dev.ellipog.stature.common.client.StatureProjectileRenderState;

import org.junit.jupiter.api.Test;

/**
 * The projectile draw factor, held to the same rule as the hitbox: exactly the inherited snapshot,
 * exactly vanilla when there is none, and never a value that could poison a pose matrix.
 *
 * <p>The dispatcher mixin cannot run headlessly, but the number it pushes and the state that
 * carries it are ordinary Java. This is also where the pooled-state reset is pinned: a fresh or
 * reset state reads 1.0 before anything is pushed.
 */
class ProjectileRenderStateTest {

    private static StatureProjectile projectile(boolean scaled, float factor) {
        return new StatureProjectile() {
            @Override
            public float stature$projectileScale() {
                return factor;
            }

            @Override
            public boolean stature$projectileScaled() {
                return scaled;
            }

            @Override
            public void stature$setProjectileScale(float ignored) {
            }

            @Override
            public boolean stature$projectileSpeedApplied() {
                return false;
            }

            @Override
            public void stature$markProjectileSpeedApplied() {
            }
        };
    }

    @Test
    void anUnscaledProjectileDrawsVanilla() {
        assertEquals(1.0F, ProjectileRenderScaling.factor(projectile(false, 4.0F)));
    }

    @Test
    void aScaledProjectileUsesTheSnapshot() {
        assertEquals(4.0F, ProjectileRenderScaling.factor(projectile(true, 4.0F)));
        assertEquals(0.25F, ProjectileRenderScaling.factor(projectile(true, 0.25F)));
    }

    @Test
    void corruptOrMissingValuesDegradeToNeutral() {
        assertEquals(1.0F, ProjectileRenderScaling.factor(projectile(true, Float.NaN)));
        assertEquals(1.0F, ProjectileRenderScaling.factor(projectile(true, 0.0F)));
        assertEquals(1.0F, ProjectileRenderScaling.factor(projectile(true, -3.0F)));
        assertEquals(1.0F, ProjectileRenderScaling.factor(null));
    }

    private static StatureProjectileRenderState freshState() {
        return new EntityRenderStateMixin() {
        };
    }

    @Test
    void anUnpushedStateReadsVanilla() {
        assertEquals(1.0F, freshState().stature$projectileScale());
    }

    @Test
    void aPushedFactorReadsBackWhole() {
        StatureProjectileRenderState state = freshState();
        state.stature$setProjectileScale(4.0F);
        assertEquals(4.0F, state.stature$projectileScale());
    }

    @Test
    void aResetStateReadsVanillaAgain() {
        StatureProjectileRenderState state = freshState();
        state.stature$setProjectileScale(4.0F);
        state.stature$setProjectileScale(1.0F);
        assertEquals(1.0F, state.stature$projectileScale());
    }

    @Test
    void aCorruptPushedValueReadsVanilla() {
        StatureProjectileRenderState state = freshState();
        state.stature$setProjectileScale(Float.NaN);
        assertEquals(1.0F, state.stature$projectileScale());
    }
}
