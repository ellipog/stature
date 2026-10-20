package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FootstepScopeTest {

    @Test
    void aQuietStepStillTravelsTheMinimum() {
        assertEquals(16.0, FootstepScope.radius(0.5F), 1e-9);
    }

    @Test
    void aGiantStepFollowsItsVolume() {
        assertEquals(38.4, FootstepScope.radius(2.4F), 1e-5);
    }

    @Test
    void theRadiusIsCappedAtSixtyFour() {
        assertEquals(64.0, FootstepScope.radius(4.0F), 1e-9);
        assertEquals(64.0, FootstepScope.radius(100.0F), 1e-9);
    }

    @Test
    void aBrokenVolumeFallsBackToVanillaRange() {
        assertEquals(16.0, FootstepScope.radius(Float.NaN), 1e-9);
        assertEquals(16.0, FootstepScope.radius(-1.0F), 1e-9);
    }
}
