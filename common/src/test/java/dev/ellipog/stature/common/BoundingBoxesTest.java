package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

/**
 * The spike's numbers, pinned: a zombie's box (0.6 x 1.95) halved in width, stretched 1.2x in
 * height, and doubled in depth is 0.300 x 2.340 x 0.600. If this ever disagrees with the game,
 * the game is what changed, and FINDINGS.md says where to look.
 */
class BoundingBoxesTest {

    private static final double DELTA = 1.0E-4;

    @Test
    void vanillaMultiplierIsVanilla() {
        AABB box = BoundingBoxes.rectangular(new Vec3(0.0, 100.0, 0.0), 0.6F, 1.95F, 1.0F);
        assertEquals(0.6, box.getXsize(), DELTA);
        assertEquals(1.95, box.getYsize(), DELTA);
        assertEquals(0.6, box.getZsize(), DELTA);
    }

    @Test
    void spikeCaseTwoA() {
        AABB box = BoundingBoxes.rectangular(new Vec3(0.0, 100.0, 0.0), 0.3F, 2.34F, 1.0F);
        assertEquals(0.3, box.getXsize(), DELTA);
        assertEquals(2.34, box.getYsize(), DELTA);
        assertEquals(0.3, box.getZsize(), DELTA);
    }

    @Test
    void spikeCaseTwoB() {
        AABB box = BoundingBoxes.rectangular(new Vec3(0.0, 100.0, 0.0), 0.3F, 2.34F, 2.0F);
        assertEquals(0.3, box.getXsize(), DELTA);
        assertEquals(2.34, box.getYsize(), DELTA);
        assertEquals(0.6, box.getZsize(), DELTA);
    }

    @Test
    void boxStandsOnThePosition() {
        AABB box = BoundingBoxes.rectangular(new Vec3(4.0, 100.0, -2.0), 0.3F, 2.34F, 2.0F);
        assertEquals(100.0, box.minY, DELTA);
        assertEquals(102.34, box.maxY, DELTA);
        assertEquals(4.0, (box.minX + box.maxX) / 2.0, DELTA);
        assertEquals(-2.0, (box.minZ + box.maxZ) / 2.0, DELTA);
    }
}
