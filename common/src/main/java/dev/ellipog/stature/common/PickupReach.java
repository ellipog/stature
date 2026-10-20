package dev.ellipog.stature.common;

import net.minecraft.world.phys.AABB;

/**
 * The pickup area a player scans for items and experience.
 *
 * <p>Vanilla inflates the player's own box by one block horizontally and half a block vertically
 * (or by the union with a vehicle). The channel scales that inflation, not the body: a 16x player
 * can reach 16 blocks without their own collision box becoming a pickup vacuum. Scaling downward
 * never removes the body box itself.
 */
public final class PickupReach {

    private PickupReach() {
    }

    /** The vanilla area with its extra reach multiplied by {@code factor}; exact at 1.0. */
    public static AABB area(AABB body, AABB vanillaArea, float factor) {
        if (!Float.isFinite(factor) || factor == 1.0F) {
            return vanillaArea;
        }
        double dx = vanillaArea.maxX - body.maxX;
        double dy = vanillaArea.maxY - body.maxY;
        double dz = vanillaArea.maxZ - body.maxZ;
        if (dx <= 0.0 || dz <= 0.0) {
            return vanillaArea;
        }
        return vanillaArea.inflate(
                dx * (factor - 1.0), Math.max(-dy, dy * (factor - 1.0)), dz * (factor - 1.0));
    }
}
