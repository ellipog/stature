package dev.ellipog.stature.common;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The box half of Spike S2, kept game-free.
 *
 * <p>Vanilla builds a square footprint: one width for X and Z
 * ({@code EntityDimensions.makeBoundingBox}). A rectangular one is pure arithmetic on a position,
 * which needs no level, no registries and no loader -- so it lives here rather than behind a
 * mixin, and the mixin that will call it only supplies the numbers. Proved by the spike
 * ({@code spike-s2/}) and pinned by the test beside this file.
 */
public final class BoundingBoxes {

    private BoundingBoxes() {
    }

    /**
     * A rectangular footprint from a square one.
     *
     * @param position        the feet position the box stands on
     * @param width           the X extent, in blocks
     * @param height          the Y extent, in blocks
     * @param depthMultiplier the Z extent as a multiple of the X extent: {@code 1.0} is vanilla,
     *                        above is deeper than wide, below is thinner
     */
    public static AABB rectangular(Vec3 position, float width, float height, float depthMultiplier) {
        double halfX = width / 2.0;
        double halfZ = halfX * depthMultiplier;
        return new AABB(
                position.x - halfX, position.y, position.z - halfZ,
                position.x + halfX, position.y + height, position.z + halfZ);
    }
}
