package dev.ellipog.stature.common;

/**
 * What a projectile inherits from the entity that fired it.
 *
 * <p>The factor is a snapshot taken when the projectile learns its owner, not a live read of the
 * owner's current scale: an arrow fired by a giant stays giant after the giant shrinks, which is
 * the same contract as an arrow's damage at the moment it was loosed. The value is saved with the
 * projectile and derived the same way on the client from the synced owner, so both sides hit the
 * same box.
 */
public interface StatureProjectile {

    /** The inherited size factor, exactly 1.0 when nothing scaled the shooter. */
    float stature$projectileScale();

    /** False until an owner has been seen; a projectile with no owner stays vanilla. */
    boolean stature$projectileScaled();

    void stature$setProjectileScale(float factor);

    /** Whether the launch velocity has already been multiplied by the inherited factor. */
    boolean stature$projectileSpeedApplied();

    void stature$markProjectileSpeedApplied();
}
