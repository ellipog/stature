package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.world.phys.AABB;

import org.junit.jupiter.api.Test;

/**
 * The displacement search order, driven without a level.
 *
 * <p>The finite search is the part that is easy to get wrong quietly: an offset that is never
 * generated is a giant that stays stuck, and an uncapped list is a search that costs more the
 * larger the entity gets. The tunnel cases come from the M5 gametest, which builds the real blocks.
 */
class DisplacementTest {

    private static final AABB GIANT = new AABB(-4.8, 0.0, -4.8, 4.8, 31.2, 4.8);

    @Test
    void upIsTriedBeforeSides() {
        List<ScaleService.Offset> offsets = ScaleService.candidateOffsets(GIANT);
        assertEquals(new ScaleService.Offset(0, 1, 0), offsets.get(0),
                "the first escape is not straight up");
        assertTrue(offsets.indexOf(new ScaleService.Offset(0, 2, 0))
                        < offsets.indexOf(new ScaleService.Offset(1, 0, 0)),
                "a side is tried before the second step up");
    }

    @Test
    void searchIsCappedAndIncludesTheTunnelEscape() {
        List<ScaleService.Offset> offsets = ScaleService.candidateOffsets(GIANT);
        assertTrue(offsets.size() <= ScaleService.MAX_DISPLACEMENT_CANDIDATES,
                "the search grew past its cap: " + offsets.size());
        assertTrue(offsets.contains(new ScaleService.Offset(0, 0, 5)),
                "the open tunnel mouth five blocks forward is not in the search");
        assertTrue(offsets.contains(new ScaleService.Offset(0, 0, -5)),
                "the open tunnel mouth five blocks backward is not in the search");
    }

    @Test
    void aSmallBoxStillGetsAUsableSearch() {
        List<ScaleService.Offset> offsets =
                ScaleService.candidateOffsets(new AABB(0.0, 0.0, 0.0, 0.6, 1.8, 0.6));
        assertTrue(offsets.size() >= 10, "a vanilla-size box got only " + offsets.size() + " offsets");
        assertEquals(new ScaleService.Offset(0, 1, 0), offsets.get(0));
    }

    @Test
    void offsetsAreUnique() {
        List<ScaleService.Offset> offsets = ScaleService.candidateOffsets(GIANT);
        assertEquals(offsets.size(), offsets.stream().distinct().count(),
                "the search contains the same offset twice");
    }
}
