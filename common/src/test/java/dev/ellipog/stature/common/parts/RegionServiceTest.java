package dev.ellipog.stature.common.parts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.frame.FrameRegion;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

class RegionServiceTest {

    private static final AABB ZOMBIE = new AABB(0.0, 100.0, 0.0, 0.6, 101.95, 0.6);

    private static final String RIG = "stature:humanoid";

    private static final List<FrameRegion> HEADSHOT =
            List.of(new FrameRegion("head", 3.0F, "headshot"));

    @Test
    void aHeadLevelHitMatchesWhileABodyHitDoesNot() {
        assertTrue(RegionService.match(HEADSHOT, RIG, ZOMBIE, Map.of(), new Vec3(0.3, 101.7, 0.3), 5)
                .map(hit -> hit.part().equals("head") && hit.tag().equals("headshot") && hit.multiplier() == 3.0F)
                .orElse(false));
        assertTrue(RegionService.match(HEADSHOT, RIG, ZOMBIE, Map.of(), new Vec3(0.3, 100.5, 0.3), 5).isEmpty());
    }

    @Test
    void theFirstRegionInFileOrderWins() {
        List<FrameRegion> regions = List.of(
                new FrameRegion("head", 2.0F, "first"),
                new FrameRegion("all", 1.5F, "second"));
        assertEquals("first", RegionService.match(regions, RIG, ZOMBIE, Map.of(), new Vec3(0.3, 101.7, 0.3), 5)
                .orElseThrow().tag());
    }

    @Test
    void unknownGroupsAreSkipped() {
        List<FrameRegion> regions = List.of(new FrameRegion("wing.left", 2.0F, "wing"));
        assertTrue(RegionService.match(regions, RIG, ZOMBIE, Map.of(), new Vec3(0.3, 101.7, 0.3), 5).isEmpty());
    }

    @Test
    void aDoubledHeadCoversDoubleHeightAboutItsMiddle() {
        Map<String, ScaleAxes> factors = Map.of("head", new ScaleAxes(2.0F, 2.0F, 2.0F));
        assertTrue(RegionService.match(HEADSHOT, RIG, ZOMBIE, factors, new Vec3(0.3, 102.1, 0.3), 5).isPresent());
        assertTrue(RegionService.match(HEADSHOT, RIG, ZOMBIE, Map.of(), new Vec3(0.3, 102.1, 0.3), 5).isEmpty());
    }

    @Test
    void aFaceOnImpactCountsAsInside() {
        assertTrue(RegionService.match(HEADSHOT, RIG, ZOMBIE, Map.of(), new Vec3(0.3, 101.95, 0.3), 5).isPresent());
        assertTrue(RegionService.match(HEADSHOT, RIG, ZOMBIE, Map.of(), new Vec3(0.6, 101.7, 0.3), 5).isPresent());
    }

    @Test
    void aZeroMultiplierStillMatches() {
        List<FrameRegion> regions = List.of(new FrameRegion("head", 0.0F, "immune"));
        assertEquals(0.0F, RegionService.match(regions, RIG, ZOMBIE, Map.of(), new Vec3(0.3, 101.7, 0.3), 5)
                .orElseThrow().multiplier());
    }
}
