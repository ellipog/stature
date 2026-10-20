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
}
