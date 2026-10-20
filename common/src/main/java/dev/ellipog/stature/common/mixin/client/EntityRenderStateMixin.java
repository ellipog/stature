package dev.ellipog.stature.common.mixin.client;

import dev.ellipog.stature.common.client.StatureProjectileRenderState;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Makes room on every render state for a projectile's inherited size.
 *
 * <p>One field on the base state rather than a new state class per renderer: the dispatcher sees
 * {@code EntityRenderState}, and a projectile renderer's state may be the vanilla base class
 * itself. The value is pushed by the dispatcher after extraction, so a reused or pooled state is
 * reset for every entity, not only for the projectiles that need it.
 *
 * <p>Deliberately not initialised. A unique field with an initialiser is merged into constructors,
 * and vanilla creates these states before anything of Stature's has run; the getter treats the
 * zero value as vanilla instead.
 */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements StatureProjectileRenderState {

    @Unique
    private float stature$projectileScale;

    @Override
    public void stature$setProjectileScale(float factor) {
        this.stature$projectileScale = factor;
    }

    @Override
    public float stature$projectileScale() {
        float factor = this.stature$projectileScale;
        return Float.isFinite(factor) && factor > 0.0F ? factor : 1.0F;
    }
}
