package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.phys.AABB;

import org.junit.jupiter.api.Test;

class PickupReachTest {

    private static final AABB BODY = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);

    @Test
    void oneLeavesTheVanillaAreaExactly() {
        AABB vanilla = BODY.inflate(1.0, 0.5, 1.0);
        assertSame(vanilla, PickupReach.area(BODY, vanilla, 1.0F));
    }

    @Test
    void aGiantReachesFurtherWithoutGrowingTheBody() {
        AABB vanilla = BODY.inflate(1.0, 0.5, 1.0);
        AABB wide = PickupReach.area(BODY, vanilla, 4.0F);
        assertEquals(4.0, wide.maxX - BODY.maxX, 1e-9);
        assertEquals(2.0, wide.maxY - BODY.maxY, 1e-9);
        assertEquals(-4.0, wide.minX - BODY.minX, 1e-9);
        assertEquals(-2.0, wide.minY - BODY.minY, 1e-9);
    }

    @Test
    void aSmallEntityDoesNotGetANegativeOrInsideOutArea() {
        AABB vanilla = BODY.inflate(1.0, 0.5, 1.0);
        AABB narrow = PickupReach.area(BODY, vanilla, 0.25F);
        assertEquals(0.25, narrow.maxX - BODY.maxX, 1e-9);
        assertEquals(0.125, narrow.maxY - BODY.maxY, 1e-9);
        assertEquals(-0.25, narrow.minX - BODY.minX, 1e-9);
        assertEquals(-0.125, narrow.minY - BODY.minY, 1e-9);
    }

    @Test
    void aCorruptFactorLeavesTheAreaAlone() {
        AABB vanilla = BODY.inflate(1.0, 1.0, 1.0);
        assertSame(vanilla, PickupReach.area(BODY, vanilla, Float.NaN));
        assertSame(vanilla, PickupReach.area(BODY, vanilla, Float.POSITIVE_INFINITY));
    }
}
