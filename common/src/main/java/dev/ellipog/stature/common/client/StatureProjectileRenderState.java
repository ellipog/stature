package dev.ellipog.stature.common.client;

/**
 * The room a projectile render state needs for its inherited size.
 *
 * <p>{@code EntityRenderState} carries only geometry sampled from the entity, and 26.3 draws the
 * state rather than the entity. This is the one extra number the projectile pass needs: the same
 * factor its hitbox already uses. It is copied in at extraction and read at submission, so the
 * model cannot drift from the box while a packet is in flight.
 *
 * <p>Implemented by a mixin, hence the {@code stature$} prefix. This interface names no client
 * class, so it is safe to load on a dedicated server even though only a client implements it.
 */
public interface StatureProjectileRenderState {

    /** Records the factor this frame should draw at. Exactly 1.0 means vanilla. */
    void stature$setProjectileScale(float factor);

    /** The factor this frame should draw at, or 1.0 when nothing has been pushed. */
    float stature$projectileScale();
}
