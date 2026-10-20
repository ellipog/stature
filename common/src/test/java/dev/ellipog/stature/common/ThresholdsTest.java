package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ThresholdsTest {

    @Test
    void stepVolumeFollowsScaleUpToTheCap() {
        assertEquals(0.15F * 16.0F, Thresholds.stepVolumeFor(0.15F, 16.0F), 1e-6F);
    }

    @Test
    void stepVolumeIsCappedAtFour() {
        assertEquals(4.0F, Thresholds.stepVolumeFor(0.3F, 16.0F), 1e-6F);
        assertEquals(4.0F, Thresholds.stepVolumeFor(4.0F, 16.0F), 1e-6F);
    }

    @Test
    void stepVolumeLeavesVanillaAlone() {
        assertEquals(0.15F, Thresholds.stepVolumeFor(0.15F, 1.0F), 1e-6F);
    }

    @Test
    void nearOneSkipsOnlyWhenNeitherTierCanApply() {
        float[] scales = {0.9F, 0.95F, 1.0F, 1.05F, 1.1F};
        float[][] edges = {
                {0.25F, 2.5F},
                {0.9F, 1.1F},
                {0.99F, 1.01F},
                {0.5F, 0.95F},
                {1.05F, 2.0F},
        };
        for (float scale : scales) {
            for (float[] edge : edges) {
                boolean tiered = scale < edge[0] || scale > edge[1];
                assertEquals(tiered, Thresholds.shouldTick(scale, edge[0], edge[1]),
                        "scale " + scale + " with edges " + edge[0] + ".." + edge[1]);
            }
        }
    }

    @Test
    void outsideTheBandAlwaysTicks() {
        for (float scale : new float[]{0.899F, 1.101F, 0.0625F, 16.0F}) {
            assertEquals(true, Thresholds.shouldTick(scale, 0.25F, 2.5F), Float.toString(scale));
        }
    }

    @Test
    void theBandBoundariesAreInclusiveButTiersAreStrict() {
        assertEquals(false, Thresholds.shouldTick(0.9F, 0.9F, 1.1F));
        assertEquals(false, Thresholds.shouldTick(1.1F, 0.9F, 1.1F));
        assertEquals(true, Thresholds.shouldTick(0.899F, 0.9F, 1.1F));
        assertEquals(true, Thresholds.shouldTick(1.101F, 0.9F, 1.1F));
    }
}
